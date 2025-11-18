package izumi.fundamentals.platform.resources

import org.scalatest.wordspec.AnyWordSpec

class PortableResourceTest extends AnyWordSpec {
  "PortableResource.embedResources" should {
    "embed resources from classpath at compile time" in {
      // Test with actual classpath resources
      val resources = PortableResource.embedResources("portable-test")

      assert(resources.nonEmpty, "Resources map should not be empty")
      assert(resources.keys.exists(_.contains("test-resource.txt")), "Should contain test-resource.txt")

      val content = resources.values.head
      assert(content.contains("This is a test resource file for PortableResource macro"))
    }

    "work with empty results when unchecked" in {
      val resources = PortableResource.embedResourcesUnchecked("nonexistent-path")
      assert(resources.isEmpty, "Should return empty map for non-existent path when unchecked")
    }
  }

  "PortableResource.embedSources" should {
    "embed resource files from filesystem at compile time" in {
      // Test with the test resource file we created
      val resources = PortableResource.embedSources(
        "fundamentals/fundamentals-platform/src/test/resources{portable-test/**/*.txt,!*DO_NOT_MATCH*}"
      )

      assert(resources.nonEmpty, "Resources map should not be empty")
      assert(resources.contains("portable-test/test-resource.txt"), "Should contain test-resource.txt")

      val content = resources("portable-test/test-resource.txt")
      assert(content.contains("This is a test resource file for PortableResource macro"))
      assert(content.contains("It contains multiple lines"))
      assert(content.contains("Line three has some content"))
    }

    "embed source files at compile time" in {
      // We'll use the test files themselves as sources
      val sources = PortableResource.embedSources(
        "fundamentals/fundamentals-platform/src/test/scala{izumi/**/*.scala,!*DO_NOT_MATCH_THIS*}"
      )

      assert(sources.nonEmpty, "Sources map should not be empty")

      // Check that we found at least this test file
      val thisTestKey = sources.keys.find(_.contains("PortableResourceTest.scala"))
      assert(thisTestKey.isDefined, "Should contain this test file itself")

      val thisTestContent = sources(thisTestKey.get)
      assert(thisTestContent.contains("PortableResourceTest"), "Content should contain class name")
      assert(thisTestContent.contains("embedSources"), "Content should contain test method name")
    }

    "filter files by glob pattern prefix" in {
      val sources = PortableResource.embedSources(
        "fundamentals/fundamentals-platform/src/test/scala{izumi/fundamentals/platform/resources/**/*.scala,!*DO_NOT_MATCH*}"
      )

      assert(sources.nonEmpty, "Should find files with specific pattern")
      assert(sources.keys.forall(_.startsWith("izumi/fundamentals/platform/resources")),
        "All files should start with pattern prefix")
    }

    "exclude files by negation pattern" in {
      val allSources = PortableResource.embedSources(
        "fundamentals/fundamentals-platform/src/test/scala{izumi/**/*.scala,!**/*DO_NOT_MATCH*}"
      )

      val filteredSources = PortableResource.embedSourcesUnchecked(
        "fundamentals/fundamentals-platform/src/test/scala{izumi/**/*.scala,!**/*PortableResourceTest*}"
      )

      assert(allSources.size > filteredSources.size,
        "Filtered sources should be fewer than all sources")
      assert(!filteredSources.keys.exists(_.contains("PortableResourceTest")),
        "Should not contain files matching negation pattern")
    }

    "control output path structure via base path placement" in {
      // By adjusting where we place the base path, we control the output path structure
      val fullPath = PortableResource.embedSources(
        "fundamentals/fundamentals-platform/src/test/scala{izumi/fundamentals/platform/resources/**/*.scala,!*DO_NOT_MATCH*}"
      )

      val partialPath = PortableResource.embedSources(
        "fundamentals/fundamentals-platform/src/test/scala/izumi/fundamentals{platform/resources/**/*.scala,!*DO_NOT_MATCH*}"
      )

      assert(fullPath.nonEmpty, "Should have entries with full path")
      assert(partialPath.nonEmpty, "Should have entries with partial path")

      // Keys in fullPath start with full izumi/fundamentals/...
      val fullKey = fullPath.keys.head
      val partialKey = partialPath.keys.head

      assert(fullKey.startsWith("izumi/fundamentals/platform/resources"),
        "Full path key should start with complete path")
      assert(partialKey.startsWith("platform/resources"),
        "Partial path key should start with platform/resources (izumi/fundamentals stripped)")
      assert(!partialKey.startsWith("izumi/"),
        "Partial path key should not include izumi prefix")
    }

    "return empty map when unchecked and no files found" in {
      val sources = PortableResource.embedSourcesUnchecked(
        "nonexistent-directory{**/*.txt}"
      )

      assert(sources.isEmpty, "Should return empty map when no files found and unchecked")
    }

    "handle trailing slash before braces in path expression" in {
      // Test with trailing slash - both forms should work
      val withSlash = PortableResource.embedSources(
        "fundamentals/fundamentals-platform/src/test/scala/{izumi/fundamentals/platform/resources/**/*.scala,!**/*DO_NOT_MATCH*}"
      )

      val withoutSlash = PortableResource.embedSources(
        "fundamentals/fundamentals-platform/src/test/scala{izumi/fundamentals/platform/resources/**/*.scala,!**/*DO_NOT_MATCH*}"
      )

      // Both should find the same files
      assert(withSlash.nonEmpty, "Should find files with trailing slash")
      assert(withoutSlash.nonEmpty, "Should find files without trailing slash")
      assert(withSlash.keySet == withoutSlash.keySet, "Both forms should find identical files")

      // Verify we found this test file
      assert(withSlash.keys.exists(_.contains("PortableResourceTest.scala")))
      assert(withoutSlash.keys.exists(_.contains("PortableResourceTest.scala")))
    }
  }
}
