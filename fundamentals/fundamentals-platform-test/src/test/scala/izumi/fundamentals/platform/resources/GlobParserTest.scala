package izumi.fundamentals.platform.resources

import izumi.fundamentals.platform.resources.GlobParser.GlobPattern
import izumi.fundamentals.testkit.AnyWordSpec

object GlobParserTest {
  private final case class ParsedCase(name: String, input: String, expected: GlobPattern)
  private final case class RegexCase(name: String, input: String, matching: List[String], nonmatching: List[String])
  private final case class PatternCase(name: String, pattern: GlobPattern, matching: List[String], nonmatching: List[String])
}

class GlobParserTest extends AnyWordSpec {
  import GlobParserTest.*

  private def checkParsing(cases: ParsedCase*): Unit = cases.foreach {
    testcase =>
      testcase.name in {
        val pattern = GlobParser.parseGlobExpr(testcase.input)
        assert(pattern.basePath == testcase.expected.basePath)
        assert(pattern.includePatterns == testcase.expected.includePatterns)
        assert(pattern.excludePatterns == testcase.expected.excludePatterns)
      }
  }

  private def checkRegexes(cases: RegexCase*): Unit = cases.foreach {
    testcase =>
      testcase.name in {
        val regex = GlobParser.globToRegex(testcase.input)
        testcase.matching.foreach(path => assert(path.matches(regex)))
        testcase.nonmatching.foreach(path => assert(!path.matches(regex)))
      }
  }

  private def checkPatterns(cases: PatternCase*): Unit = cases.foreach {
    testcase =>
      testcase.name in {
        testcase.matching.foreach(path => assert(GlobParser.matchesPattern(path, testcase.pattern)))
        testcase.nonmatching.foreach(path => assert(!GlobParser.matchesPattern(path, testcase.pattern)))
      }
  }

  "GlobParser.parseGlobExpr" should {
    checkParsing(
      ParsedCase("parse explicit base path syntax", "src/main/scala{**/*.scala,!**/*Test*}", GlobPattern("src/main/scala", List("**/*.scala"), List("**/*Test*"))),
      ParsedCase(
        "parse multiple include patterns with explicit base path",
        "config{*.conf,*.json,*.yaml}",
        GlobPattern("config", List("*.conf", "*.json", "*.yaml"), List.empty),
      ),
      ParsedCase(
        "parse multiple include and exclude patterns",
        "data{**/*.csv,**/*.json,!**/test/*,!**/*backup*}",
        GlobPattern("data", List("**/*.csv", "**/*.json"), List("**/test/*", "**/*backup*")),
      ),
      ParsedCase("parse legacy syntax with auto-extracted base path", "src/main/scala/**/*.scala", GlobPattern("src/main/scala", List("**/*.scala"), List.empty)),
      ParsedCase(
        "parse legacy syntax with negation and auto-extracted base path",
        "src/**/*.scala,!**/test/*",
        GlobPattern("src", List("**/*.scala"), List("**/test/*")),
      ),
      ParsedCase("handle pattern with no wildcards", "some/path/file.txt", GlobPattern("some/path/file.txt", List(""), List.empty)),
      ParsedCase("handle empty base path for pattern starting with wildcard", "*.txt", GlobPattern("", List("*.txt"), List.empty)),
      ParsedCase("trim whitespace in patterns", "src{ *.scala , *.java , !*Test* }", GlobPattern("src", List("*.scala", "*.java"), List("*Test*"))),
      ParsedCase("handle empty patterns gracefully", "src{*.scala,,*.java}", GlobPattern("src", List("*.scala", "*.java"), List.empty)),
    )
  }

  "GlobParser.globToRegex" should {
    "convert simple wildcard *" in {
      val regex = GlobParser.globToRegex("*.txt")
      assert(regex == "^[^/]*?\\.txt$")

      assert("file.txt".matches(regex))
      assert("test.txt".matches(regex))
      assert(!"dir/file.txt".matches(regex))
    }

    checkRegexes(
      RegexCase("convert recursive wildcard **", "**/*.txt", List("dir/file.txt", "a/b/c/file.txt", "file.txt"), List.empty),
      RegexCase("convert ** at start of pattern", "**/test.txt", List("test.txt", "dir/test.txt", "a/b/c/test.txt"), List.empty),
      RegexCase("convert ** in middle of pattern", "src/**/Test.scala", List("src/Test.scala", "src/main/Test.scala", "src/main/scala/Test.scala"), List.empty),
      RegexCase("convert ? wildcard", "file?.txt", List("file1.txt", "fileA.txt"), List("file.txt", "file12.txt", "dir/file1.txt")),
      RegexCase("escape special regex characters", "file.txt", List("file.txt"), List("fileXtxt")),
    )

    "escape parentheses and brackets" in {
      val regex1 = GlobParser.globToRegex("file(1).txt")
      assert("file(1).txt".matches(regex1))

      val regex2 = GlobParser.globToRegex("file[a].txt")
      assert("file[a].txt".matches(regex2))
    }

    checkRegexes(
      RegexCase("handle multiple wildcards", "*/*.txt", List("dir/file.txt"), List("file.txt", "dir/sub/file.txt")),
      RegexCase(
        "handle complex patterns",
        "src/**/*Test*.scala",
        List("src/FooTest.scala", "src/main/TestBar.scala", "src/main/scala/MyTestSuite.scala"),
        List("src/main/scala/MyClass.scala"),
      ),
    )
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
    checkPatterns(
      PatternCase("match with single include pattern", GlobPattern("", List("*.txt"), List.empty), List("file.txt"), List("file.scala")),
      PatternCase("match with multiple include patterns", GlobPattern("", List("*.txt", "*.md"), List.empty), List("file.txt", "README.md"), List("file.scala")),
      PatternCase(
        "exclude with exclude patterns",
        GlobPattern("", List("**/*.scala"), List("**/*Test*")),
        List("src/Main.scala"),
        List("src/MainTest.scala", "src/test/FooTest.scala"),
      ),
      PatternCase(
        "match with multiple include and exclude patterns",
        GlobPattern("", List("**/*.scala", "**/*.java"), List("**/*Test*", "**/target/*")),
        List("src/Main.scala", "src/Main.java"),
        List("src/MainTest.scala", "target/Main.scala", "src/Main.txt"),
      ),
      PatternCase("require at least one include pattern to match", GlobPattern("", List("*.txt"), List("*test*")), List.empty, List("file.scala")),
    )
  }

  "GlobParser Windows compatibility" should {
    "normalize backslash-separated paths in matchesGlob" in {
      assert(GlobParser.matchesGlob("dir\\file.txt", "dir/file.txt"))
      assert(GlobParser.matchesGlob("dir\\file.txt", "*/file.txt"))
      assert(GlobParser.matchesGlob("a\\b\\c\\file.txt", "**/*.txt"))
      assert(GlobParser.matchesGlob("src\\main\\Test.scala", "src/**/Test.scala"))
      assert(!GlobParser.matchesGlob("a\\b\\file.txt", "*/file.txt"))
    }

    checkPatterns(
      PatternCase(
        "normalize backslash-separated paths in matchesPattern",
        GlobPattern("", List("**/*.scala"), List("**/*Test*")),
        List("src\\Main.scala"),
        List("src\\MainTest.scala"),
      ),
    )

    checkParsing(
      ParsedCase(
        "normalize backslashes in parseGlobExpr input",
        "src\\main\\scala{**\\*.scala,!**\\*Test*}",
        GlobPattern("src/main/scala", List("**/*.scala"), List("**/*Test*")),
      ),
      ParsedCase("normalize backslashes in legacy parseGlobExpr input", "src\\main\\scala\\**\\*.scala", GlobPattern("src/main/scala", List("**/*.scala"), List.empty)),
    )
  }

  "GlobParser edge cases" should {

    checkParsing(
      ParsedCase("handle pattern with only exclude patterns", "src{!**/*Test*}", GlobPattern("src", List.empty, List("**/*Test*"))),
      ParsedCase("handle empty braces", "src{}", GlobPattern("src", List.empty, List.empty)),
      ParsedCase(
        "handle base path with trailing slash before braces",
        "src/main/scala/{**/*.scala,!**/*Test*}",
        GlobPattern("src/main/scala/", List("**/*.scala"), List("**/*Test*")),
      ),
      ParsedCase(
        "handle base path without trailing slash before braces",
        "src/main/scala{**/*.scala,!**/*Test*}",
        GlobPattern("src/main/scala", List("**/*.scala"), List("**/*Test*")),
      ),
    )

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

    checkRegexes(
      RegexCase("match patterns starting with **/", "**/test/*", List("test/file.txt", "a/b/test/file.txt"), List.empty),
    )

    "not match across directory boundaries with *" in {
      assert(!GlobParser.matchesGlob("a/b/file.txt", "*/file.txt"))
      assert(GlobParser.matchesGlob("a/file.txt", "*/file.txt"))
    }
  }
}
