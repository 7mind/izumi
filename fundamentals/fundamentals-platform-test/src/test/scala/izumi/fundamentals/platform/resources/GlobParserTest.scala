package izumi.fundamentals.platform.resources

import org.scalatest.wordspec.AnyWordSpec

class GlobParserTest extends AnyWordSpec {

  "GlobParser.parseGlobExpr" should {
    "parse explicit base path syntax" in {
      val pattern = GlobParser.parseGlobExpr("src/main/scala{**/*.scala,!**/*Test*}")

      assert(pattern.basePath == "src/main/scala")
      assert(pattern.includePatterns == List("**/*.scala"))
      assert(pattern.excludePatterns == List("**/*Test*"))
    }

    "parse multiple include patterns with explicit base path" in {
      val pattern = GlobParser.parseGlobExpr("config{*.conf,*.json,*.yaml}")

      assert(pattern.basePath == "config")
      assert(pattern.includePatterns == List("*.conf", "*.json", "*.yaml"))
      assert(pattern.excludePatterns.isEmpty)
    }

    "parse multiple include and exclude patterns" in {
      val pattern = GlobParser.parseGlobExpr("data{**/*.csv,**/*.json,!**/test/*,!**/*backup*}")

      assert(pattern.basePath == "data")
      assert(pattern.includePatterns == List("**/*.csv", "**/*.json"))
      assert(pattern.excludePatterns == List("**/test/*", "**/*backup*"))
    }

    "parse legacy syntax with auto-extracted base path" in {
      val pattern = GlobParser.parseGlobExpr("src/main/scala/**/*.scala")

      assert(pattern.basePath == "src/main/scala")
      assert(pattern.includePatterns == List("**/*.scala"))
      assert(pattern.excludePatterns.isEmpty)
    }

    "parse legacy syntax with negation and auto-extracted base path" in {
      val pattern = GlobParser.parseGlobExpr("src/**/*.scala,!**/test/*")

      assert(pattern.basePath == "src")
      assert(pattern.includePatterns == List("**/*.scala"))
      assert(pattern.excludePatterns == List("**/test/*"))
    }

    "handle pattern with no wildcards" in {
      val pattern = GlobParser.parseGlobExpr("some/path/file.txt")

      assert(pattern.basePath == "some/path/file.txt")
      assert(pattern.includePatterns == List(""))
    }

    "handle empty base path for pattern starting with wildcard" in {
      val pattern = GlobParser.parseGlobExpr("*.txt")

      assert(pattern.basePath == "")
      assert(pattern.includePatterns == List("*.txt"))
    }

    "trim whitespace in patterns" in {
      val pattern = GlobParser.parseGlobExpr("src{ *.scala , *.java , !*Test* }")

      assert(pattern.includePatterns == List("*.scala", "*.java"))
      assert(pattern.excludePatterns == List("*Test*"))
    }

    "handle empty patterns gracefully" in {
      val pattern = GlobParser.parseGlobExpr("src{*.scala,,*.java}")

      assert(pattern.includePatterns == List("*.scala", "*.java"))
    }
  }

  "GlobParser.globToRegex" should {
    "convert simple wildcard *" in {
      val regex = GlobParser.globToRegex("*.txt")
      assert(regex == "^[^/]*?\\.txt$")

      assert("file.txt".matches(regex))
      assert("test.txt".matches(regex))
      assert(!"dir/file.txt".matches(regex))
    }

    "convert recursive wildcard **" in {
      val regex = GlobParser.globToRegex("**/*.txt")
      assert("dir/file.txt".matches(regex))
      assert("a/b/c/file.txt".matches(regex))
      assert("file.txt".matches(regex))
    }

    "convert ** at start of pattern" in {
      val regex = GlobParser.globToRegex("**/test.txt")
      assert("test.txt".matches(regex))
      assert("dir/test.txt".matches(regex))
      assert("a/b/c/test.txt".matches(regex))
    }

    "convert ** in middle of pattern" in {
      val regex = GlobParser.globToRegex("src/**/Test.scala")
      assert("src/Test.scala".matches(regex))
      assert("src/main/Test.scala".matches(regex))
      assert("src/main/scala/Test.scala".matches(regex))
    }

    "convert ? wildcard" in {
      val regex = GlobParser.globToRegex("file?.txt")
      assert("file1.txt".matches(regex))
      assert("fileA.txt".matches(regex))
      assert(!"file.txt".matches(regex))
      assert(!"file12.txt".matches(regex))
      assert(!"dir/file1.txt".matches(regex))
    }

    "escape special regex characters" in {
      val regex = GlobParser.globToRegex("file.txt")
      assert("file.txt".matches(regex))
      assert(!"fileXtxt".matches(regex))
    }

    "escape parentheses and brackets" in {
      val regex1 = GlobParser.globToRegex("file(1).txt")
      assert("file(1).txt".matches(regex1))

      val regex2 = GlobParser.globToRegex("file[a].txt")
      assert("file[a].txt".matches(regex2))
    }

    "handle multiple wildcards" in {
      val regex = GlobParser.globToRegex("*/*.txt")
      assert("dir/file.txt".matches(regex))
      assert(!"file.txt".matches(regex))
      assert(!"dir/sub/file.txt".matches(regex))
    }

    "handle complex patterns" in {
      val regex = GlobParser.globToRegex("src/**/*Test*.scala")
      assert("src/FooTest.scala".matches(regex))
      assert("src/main/TestBar.scala".matches(regex))
      assert("src/main/scala/MyTestSuite.scala".matches(regex))
      assert(!"src/main/scala/MyClass.scala".matches(regex))
    }
  }

  "GlobParser.matchesGlob" should {
    "match simple patterns" in {
      assert(GlobParser.matchesGlob("test.txt", "*.txt"))
      assert(GlobParser.matchesGlob("file.scala", "*.scala"))
      assert(!GlobParser.matchesGlob("file.txt", "*.scala"))
    }

    "match recursive patterns" in {
      assert(GlobParser.matchesGlob("a/b/c/file.txt", "**/*.txt"))
      assert(GlobParser.matchesGlob("file.txt", "**/*.txt"))
      assert(!GlobParser.matchesGlob("a/b/c/file.scala", "**/*.txt"))
    }

    "match patterns with multiple wildcards" in {
      assert(GlobParser.matchesGlob("dir/file.txt", "*/*.txt"))
      assert(GlobParser.matchesGlob("a/b/c/Test.scala", "**/*/Test.scala"))
      assert(GlobParser.matchesGlob("src/test/MyTest.scala", "src/**/*Test*.scala"))
    }

    "match patterns with ?" in {
      assert(GlobParser.matchesGlob("file1.txt", "file?.txt"))
      assert(GlobParser.matchesGlob("fileA.txt", "file?.txt"))
      assert(!GlobParser.matchesGlob("file.txt", "file?.txt"))
      assert(!GlobParser.matchesGlob("file12.txt", "file?.txt"))
    }
  }

  "GlobParser.matchesPattern" should {
    "match with single include pattern" in {
      val pattern = GlobParser.GlobPattern("", List("*.txt"), List.empty)
      assert(GlobParser.matchesPattern("file.txt", pattern))
      assert(!GlobParser.matchesPattern("file.scala", pattern))
    }

    "match with multiple include patterns" in {
      val pattern = GlobParser.GlobPattern("", List("*.txt", "*.md"), List.empty)
      assert(GlobParser.matchesPattern("file.txt", pattern))
      assert(GlobParser.matchesPattern("README.md", pattern))
      assert(!GlobParser.matchesPattern("file.scala", pattern))
    }

    "exclude with exclude patterns" in {
      val pattern = GlobParser.GlobPattern("", List("**/*.scala"), List("**/*Test*"))
      assert(GlobParser.matchesPattern("src/Main.scala", pattern))
      assert(!GlobParser.matchesPattern("src/MainTest.scala", pattern))
      assert(!GlobParser.matchesPattern("src/test/FooTest.scala", pattern))
    }

    "match with multiple include and exclude patterns" in {
      val pattern = GlobParser.GlobPattern(
        "",
        List("**/*.scala", "**/*.java"),
        List("**/*Test*", "**/target/*")
      )

      assert(GlobParser.matchesPattern("src/Main.scala", pattern))
      assert(GlobParser.matchesPattern("src/Main.java", pattern))
      assert(!GlobParser.matchesPattern("src/MainTest.scala", pattern))
      assert(!GlobParser.matchesPattern("target/Main.scala", pattern))
      assert(!GlobParser.matchesPattern("src/Main.txt", pattern))
    }

    "require at least one include pattern to match" in {
      val pattern = GlobParser.GlobPattern("", List("*.txt"), List("*test*"))
      assert(!GlobParser.matchesPattern("file.scala", pattern))
    }
  }

  "GlobParser Windows compatibility" should {
    "normalize backslash-separated paths in matchesGlob" in {
      assert(GlobParser.matchesGlob("dir\\file.txt", "dir/file.txt"))
      assert(GlobParser.matchesGlob("dir\\file.txt", "*/file.txt"))
      assert(GlobParser.matchesGlob("a\\b\\c\\file.txt", "**/*.txt"))
      assert(GlobParser.matchesGlob("src\\main\\Test.scala", "src/**/Test.scala"))
      assert(!GlobParser.matchesGlob("a\\b\\file.txt", "*/file.txt"))
    }

    "normalize backslash-separated paths in matchesPattern" in {
      val pattern = GlobParser.GlobPattern("", List("**/*.scala"), List("**/*Test*"))
      assert(GlobParser.matchesPattern("src\\Main.scala", pattern))
      assert(!GlobParser.matchesPattern("src\\MainTest.scala", pattern))
    }

    "normalize backslashes in parseGlobExpr input" in {
      val pattern = GlobParser.parseGlobExpr("src\\main\\scala{**\\*.scala,!**\\*Test*}")
      assert(pattern.basePath == "src/main/scala")
      assert(pattern.includePatterns == List("**/*.scala"))
      assert(pattern.excludePatterns == List("**/*Test*"))
    }

    "normalize backslashes in legacy parseGlobExpr input" in {
      val pattern = GlobParser.parseGlobExpr("src\\main\\scala\\**\\*.scala")
      assert(pattern.basePath == "src/main/scala")
      assert(pattern.includePatterns == List("**/*.scala"))
    }
  }

  "GlobParser edge cases" should {

    "handle pattern with only exclude patterns" in {
      val pattern = GlobParser.parseGlobExpr("src{!**/*Test*}")
      assert(pattern.includePatterns.isEmpty)
      assert(pattern.excludePatterns == List("**/*Test*"))
    }

    "handle empty braces" in {
      val pattern = GlobParser.parseGlobExpr("src{}")
      assert(pattern.basePath == "src")
      assert(pattern.includePatterns.isEmpty)
      assert(pattern.excludePatterns.isEmpty)
    }

    "handle base path with trailing slash before braces" in {
      val pattern = GlobParser.parseGlobExpr("src/main/scala/{**/*.scala,!**/*Test*}")
      assert(pattern.basePath == "src/main/scala/")
      assert(pattern.includePatterns == List("**/*.scala"))
      assert(pattern.excludePatterns == List("**/*Test*"))
    }

    "handle base path without trailing slash before braces" in {
      val pattern = GlobParser.parseGlobExpr("src/main/scala{**/*.scala,!**/*Test*}")
      assert(pattern.basePath == "src/main/scala")
      assert(pattern.includePatterns == List("**/*.scala"))
      assert(pattern.excludePatterns == List("**/*Test*"))
    }

    "normalize paths correctly with trailing slash" in {
      // Both forms should work equivalently
      val withSlash = GlobParser.parseGlobExpr("path/to/dir/{*.txt}")
      val withoutSlash = GlobParser.parseGlobExpr("path/to/dir{*.txt}")

      assert(withSlash.basePath == "path/to/dir/")
      assert(withoutSlash.basePath == "path/to/dir")
      assert(withSlash.includePatterns == withoutSlash.includePatterns)
    }

    "handle nested path separators in patterns" in {
      assert(GlobParser.matchesGlob("a/b/c/d/file.txt", "**/file.txt"))
      assert(GlobParser.matchesGlob("a/b/c/d/file.txt", "**/**/file.txt"))
    }

    "match patterns starting with **/" in {
      val regex = GlobParser.globToRegex("**/test/*")
      assert("test/file.txt".matches(regex))
      assert("a/b/test/file.txt".matches(regex))
    }

    "not match across directory boundaries with *" in {
      assert(!GlobParser.matchesGlob("a/b/file.txt", "*/file.txt"))
      assert(GlobParser.matchesGlob("a/file.txt", "*/file.txt"))
    }
  }
}
