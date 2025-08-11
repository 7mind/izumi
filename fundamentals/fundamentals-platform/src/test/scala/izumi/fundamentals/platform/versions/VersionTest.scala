package izumi.fundamentals.platform.versions

import izumi.fundamentals.collections.nonempty.NEList
import org.scalatest.wordspec.AnyWordSpec

class VersionTest extends AnyWordSpec {
  "Version.parseSemver" should {
    "parse basic semantic versions" in {
      val result = Version.parseSemver("1.2.3")
      assert(result.contains(Version.Semver(1, 2, 3, None, None)))
    }

    "parse semantic versions with pre-release" in {
      val result = Version.parseSemver("1.0.0-alpha")
      assert(result.contains(Version.Semver(1, 0, 0, Some("alpha"), None)))
    }

    "parse semantic versions with pre-release and build metadata" in {
      val result = Version.parseSemver("1.0.0-alpha.1+20130313144700")
      assert(result.contains(Version.Semver(1, 0, 0, Some("alpha.1"), Some("20130313144700"))))
    }

    "parse semantic versions with build metadata only" in {
      val result = Version.parseSemver("1.0.0+20130313144700")
      assert(result.contains(Version.Semver(1, 0, 0, None, Some("20130313144700"))))
    }

    "parse complex pre-release identifiers" in {
      val result = Version.parseSemver("1.0.0-rc.1.2.3")
      assert(result.contains(Version.Semver(1, 0, 0, Some("rc.1.2.3"), None)))
    }

    "return None for invalid formats" in {
      assert(Version.parseSemver("1.2").isEmpty)
      assert(Version.parseSemver("1.2.3.4").isEmpty)
      assert(Version.parseSemver("a.b.c").isEmpty)
      assert(Version.parseSemver("1.2.a").isEmpty)
      assert(Version.parseSemver("").isEmpty)
      assert(Version.parseSemver("v1.2.3").isEmpty)
    }

    "handle edge cases" in {
      assert(Version.parseSemver("0.0.0").contains(Version.Semver(0, 0, 0, None, None)))
      assert(Version.parseSemver("10.20.30").contains(Version.Semver(10, 20, 30, None, None)))
    }
  }

  "Version.parseCanonical" should {
    "parse simple version numbers" in {
      val result = Version.parseCanonical("1.2.3")
      assert(result.contains(Version.Canonical(NEList(1, 2, 3), List.empty)))
    }

    "parse versions with qualifiers" in {
      val result = Version.parseCanonical("1.2.3-SNAPSHOT")
      assert(result.contains(Version.Canonical(NEList(1, 2, 3), List("SNAPSHOT"))))
    }

    "parse versions with multiple qualifiers" in {
      val result = Version.parseCanonical("1.2.3-alpha-SNAPSHOT-TEST")
      assert(result.contains(Version.Canonical(NEList(1, 2, 3), List("alpha", "SNAPSHOT", "TEST"))))
    }

    "parse single component versions" in {
      val result = Version.parseCanonical("42")
      assert(result.contains(Version.Canonical(NEList(42), List.empty)))
    }

    "parse two component versions" in {
      val result = Version.parseCanonical("1.0")
      assert(result.contains(Version.Canonical(NEList(1, 0), List.empty)))
    }

    "parse four or more component versions" in {
      val result = Version.parseCanonical("1.2.3.4")
      assert(result.contains(Version.Canonical(NEList(1, 2, 3, 4), List.empty)))
    }

    "parse versions with qualifiers and multiple components" in {
      val result = Version.parseCanonical("1.0-beta-1")
      assert(result.contains(Version.Canonical(NEList(1, 0), List("beta", "1"))))
    }

    "return None for invalid formats" in {
      assert(Version.parseCanonical("").isEmpty)
      assert(Version.parseCanonical("a.b.c").isEmpty)
      assert(Version.parseCanonical("1.2.a").isEmpty)
      assert(Version.parseCanonical("-SNAPSHOT").isEmpty)
    }
  }

  "Version.parse" should {
    "parse as Semver when possible" in {
      val result = Version.parse("1.2.3")
      assert(result == Version.Semver(1, 2, 3, None, None))
    }

    "parse as Semver with pre-release and build" in {
      val result = Version.parse("1.0.0-alpha+build")
      assert(result == Version.Semver(1, 0, 0, Some("alpha"), Some("build")))
    }

    "fall back to Canonical for non-semver versions" in {
      val result = Version.parse("1.2")
      assert(result == Version.Canonical(NEList(1, 2), List.empty))
    }

    "fall back to Canonical for four component versions" in {
      val result = Version.parse("1.2.3.4")
      assert(result == Version.Canonical(NEList(1, 2, 3, 4), List.empty))
    }

    "return Unknown for invalid versions" in {
      val result = Version.parse("not-a-version")
      assert(result == Version.Unknown("not-a-version"))
    }

    "return Unknown for empty string" in {
      val result = Version.parse("")
      assert(result == Version.Unknown(""))
    }

    "handle various version formats" in {
      assert(Version.parse("v1.2.3") == Version.Unknown("v1.2.3"))
      assert(Version.parse("1.2.3-SNAPSHOT") == Version.Semver(1, 2, 3, Some("SNAPSHOT"), None))
      assert(Version.parse("42") == Version.Canonical(NEList(42), List.empty))
    }
  }

  "Version model conversions" should {
    "convert Semver to Canonical" in {
      val semver = Version.Semver(1, 2, 3, Some("alpha"), Some("build"))
      val canonical = semver.canonical
      assert(canonical == Version.Canonical(NEList(1, 2, 3), List("alpha", "build")))
    }

    "convert simple Canonical to Semver" in {
      val canonical = Version.Canonical(NEList(1, 2, 3), List.empty)
      assert(canonical.toSemver.contains(Version.Semver(1, 2, 3, None, None)))
    }

    "convert Canonical with one qualifier to Semver" in {
      val canonical = Version.Canonical(NEList(1, 2, 3), List("alpha"))
      assert(canonical.toSemver.contains(Version.Semver(1, 2, 3, Some("alpha"), None)))
    }

    "not convert Canonical with wrong component count to Semver" in {
      val canonical1 = Version.Canonical(NEList(1, 2), List.empty)
      assert(canonical1.toSemver.isEmpty)

      val canonical2 = Version.Canonical(NEList(1, 2, 3, 4), List.empty)
      assert(canonical2.toSemver.isEmpty)
    }

    "not convert Canonical with too many qualifiers to Semver" in {
      val canonical = Version.Canonical(NEList(1, 2, 3), List("alpha", "beta", "gamma"))
      assert(canonical.toSemver.isEmpty)
    }
  }

  "Version toString" should {
    "format Semver correctly" in {
      assert(Version.Semver(1, 2, 3, None, None).toString == "1.2.3")
      assert(Version.Semver(1, 2, 3, Some("alpha"), None).toString == "1.2.3-alpha")
      assert(Version.Semver(1, 2, 3, None, Some("build")).toString == "1.2.3+build")
      assert(Version.Semver(1, 2, 3, Some("alpha"), Some("build")).toString == "1.2.3-alpha+build")
    }

    "format Canonical correctly" in {
      assert(Version.Canonical(NEList(1, 2, 3), List.empty).toString == "1.2.3")
      assert(Version.Canonical(NEList(1, 2, 3), List("SNAPSHOT")).toString == "1.2.3-SNAPSHOT")
      assert(Version.Canonical(NEList(1, 2, 3), List("alpha", "1")).toString == "1.2.3-alpha-1")
      assert(Version.Canonical(NEList(1), List("beta")).toString == "1-beta")
    }
  }

  "Canonical ordering" should {
    "order by components first" in {
      val versions = List(
        Version.Canonical(NEList(2, 0, 0), List.empty),
        Version.Canonical(NEList(1, 0, 0), List.empty),
        Version.Canonical(NEList(1, 1, 0), List.empty),
        Version.Canonical(NEList(1, 0, 1), List.empty),
      )
      val sorted = versions.sorted
      assert(
        sorted == List(
          Version.Canonical(NEList(1, 0, 0), List.empty),
          Version.Canonical(NEList(1, 0, 1), List.empty),
          Version.Canonical(NEList(1, 1, 0), List.empty),
          Version.Canonical(NEList(2, 0, 0), List.empty),
        )
      )
    }

    "consider versions without qualifiers as newer" in {
      val v1 = Version.Canonical(NEList(1, 0, 0), List.empty)
      val v2 = Version.Canonical(NEList(1, 0, 0), List("SNAPSHOT"))
      assert(implicitly[Ordering[Version.Canonical]].compare(v1, v2) > 0)
    }

    "order qualifiers lexicographically" in {
      val versions = List(
        Version.Canonical(NEList(1, 0, 0), List("beta")),
        Version.Canonical(NEList(1, 0, 0), List("alpha")),
        Version.Canonical(NEList(1, 0, 0), List("rc")),
      )
      val sorted = versions.sorted
      assert(
        sorted == List(
          Version.Canonical(NEList(1, 0, 0), List("alpha")),
          Version.Canonical(NEList(1, 0, 0), List("beta")),
          Version.Canonical(NEList(1, 0, 0), List("rc")),
        )
      )
    }

    "handle different component lengths" in {
      val v1 = Version.Canonical(NEList(1, 0), List.empty)
      val v2 = Version.Canonical(NEList(1, 0, 0), List.empty)
      assert(implicitly[Ordering[Version.Canonical]].compare(v1, v2) < 0)
    }
  }

  "Semver ordering" should {
    "follow semantic versioning precedence rules" in {
      val versions = List(
        Version.Semver(2, 0, 0, None, None),
        Version.Semver(1, 0, 0, None, None),
        Version.Semver(1, 1, 0, None, None),
        Version.Semver(1, 0, 1, None, None),
      )
      val sorted = versions.sorted
      assert(
        sorted == List(
          Version.Semver(1, 0, 0, None, None),
          Version.Semver(1, 0, 1, None, None),
          Version.Semver(1, 1, 0, None, None),
          Version.Semver(2, 0, 0, None, None),
        )
      )
    }

    "consider pre-release versions as lower precedence" in {
      val v1 = Version.Semver(1, 0, 0, None, None)
      val v2 = Version.Semver(1, 0, 0, Some("alpha"), None)
      assert(implicitly[Ordering[Version.Semver]].compare(v1, v2) > 0)
    }

    "order pre-release identifiers correctly" in {
      val versions = List(
        Version.Semver(1, 0, 0, Some("alpha.1"), None),
        Version.Semver(1, 0, 0, Some("alpha"), None),
        Version.Semver(1, 0, 0, Some("beta"), None),
        Version.Semver(1, 0, 0, Some("rc.1"), None),
      )
      val sorted = versions.sorted
      assert(
        sorted == List(
          Version.Semver(1, 0, 0, Some("alpha"), None),
          Version.Semver(1, 0, 0, Some("alpha.1"), None),
          Version.Semver(1, 0, 0, Some("beta"), None),
          Version.Semver(1, 0, 0, Some("rc.1"), None),
        )
      )
    }

    "handle numeric vs alphanumeric pre-release identifiers" in {
      val v1 = Version.Semver(1, 0, 0, Some("1"), None)
      val v2 = Version.Semver(1, 0, 0, Some("alpha"), None)
      assert(implicitly[Ordering[Version.Semver]].compare(v1, v2) < 0)
    }

    "compare numeric pre-release identifiers numerically" in {
      val v1 = Version.Semver(1, 0, 0, Some("1.2.3"), None)
      val v2 = Version.Semver(1, 0, 0, Some("1.10.3"), None)
      assert(implicitly[Ordering[Version.Semver]].compare(v1, v2) < 0)
    }

    "ignore build metadata in precedence" in {
      val v1 = Version.Semver(1, 0, 0, None, Some("build1"))
      val v2 = Version.Semver(1, 0, 0, None, Some("build2"))
      assert(implicitly[Ordering[Version.Semver]].compare(v1, v2) == 0)
    }

    "order correctly when both pre-release and build metadata are present" in {
      val versions = List(
        Version.Semver(1, 0, 0, Some("beta"), Some("build.1")),
        Version.Semver(1, 0, 0, Some("alpha"), Some("build.2")),
        Version.Semver(1, 0, 0, None, Some("build.3")),
        Version.Semver(1, 0, 0, Some("rc"), Some("build.4")),
      )
      val sorted = versions.sorted
      assert(
        sorted == List(
          Version.Semver(1, 0, 0, Some("alpha"), Some("build.2")),
          Version.Semver(1, 0, 0, Some("beta"), Some("build.1")),
          Version.Semver(1, 0, 0, Some("rc"), Some("build.4")),
          Version.Semver(1, 0, 0, None, Some("build.3")),
        )
      )
    }

    "ignore build metadata when comparing versions with same pre-release" in {
      val v1 = Version.Semver(1, 0, 0, Some("alpha"), Some("build.1"))
      val v2 = Version.Semver(1, 0, 0, Some("alpha"), Some("build.2"))
      assert(implicitly[Ordering[Version.Semver]].compare(v1, v2) == 0)
    }

    "compare complex versions with pre-release and build metadata" in {
      val v1 = Version.Semver(2, 0, 0, Some("rc.1"), Some("build.123"))
      val v2 = Version.Semver(2, 0, 0, Some("rc.10"), Some("build.456"))
      assert(implicitly[Ordering[Version.Semver]].compare(v1, v2) < 0)

      val v3 = Version.Semver(1, 9, 9, Some("beta.final"), Some("sha.5114f85"))
      val v4 = Version.Semver(2, 0, 0, Some("alpha"), Some("sha.cdc6c41"))
      assert(implicitly[Ordering[Version.Semver]].compare(v3, v4) < 0)
    }
  }

  "Unknown ordering" should {
    "order lexicographically by version string" in {
      val versions = List(
        Version.Unknown("v2.0"),
        Version.Unknown("v1.0"),
        Version.Unknown("not-a-version"),
        Version.Unknown("1.0-custom"),
      )
      val sorted = versions.sorted
      assert(
        sorted == List(
          Version.Unknown("1.0-custom"),
          Version.Unknown("not-a-version"),
          Version.Unknown("v1.0"),
          Version.Unknown("v2.0"),
        )
      )
    }
  }

  "Global Version ordering" should {
    "order same type versions using their specific ordering" in {
      val versions: List[Version] = List(
        Version.Canonical(NEList(2, 0, 0), List.empty),
        Version.Semver(1, 0, 0, None, None),
        Version.Unknown("v1.0"),
        Version.Canonical(NEList(1, 0, 0), List.empty),
        Version.Semver(2, 0, 0, None, None),
        Version.Unknown("v2.0"),
      )

      val canonicals = versions.collect { case c: Version.Canonical => c }.sorted
      assert(
        canonicals == List(
          Version.Canonical(NEList(1, 0, 0), List.empty),
          Version.Canonical(NEList(2, 0, 0), List.empty),
        )
      )

      val semvers = versions.collect { case s: Version.Semver => s }.sorted
      assert(
        semvers == List(
          Version.Semver(1, 0, 0, None, None),
          Version.Semver(2, 0, 0, None, None),
        )
      )

      val unknowns = versions.collect { case u: Version.Unknown => u }.sorted
      assert(
        unknowns == List(
          Version.Unknown("v1.0"),
          Version.Unknown("v2.0"),
        )
      )
    }

    "compare Semver vs Canonical using canonical conversion" in {
      val semver = Version.Semver(1, 2, 3, Some("alpha"), Some("build"))
      val canonical = Version.Canonical(NEList(1, 2, 3), List("beta"))

      assert(implicitly[Ordering[Version]].compare(semver, canonical) < 0)

      val semver2 = Version.Semver(1, 2, 4, None, None)
      val canonical2 = Version.Canonical(NEList(1, 2, 3), List.empty)

      assert(implicitly[Ordering[Version]].compare(semver2, canonical2) > 0)
    }

    "compare Canonical vs Unknown using toString" in {
      val canonical = Version.Canonical(NEList(1, 2, 3), List("SNAPSHOT"))
      val unknown = Version.Unknown("1.2.3-RELEASE")

      assert(implicitly[Ordering[Version]].compare(canonical, unknown) > 0)

      val canonical2 = Version.Canonical(NEList(1, 0, 0), List.empty)
      val unknown2 = Version.Unknown("1.0.0")

      assert(implicitly[Ordering[Version]].compare(canonical2, unknown2) == 0)
    }

    "compare Semver vs Unknown using toString" in {
      val semver = Version.Semver(1, 0, 0, Some("alpha"), Some("build"))
      val unknown = Version.Unknown("1.0.0-beta")

      assert(implicitly[Ordering[Version]].compare(semver, unknown) < 0)

      val semver2 = Version.Semver(2, 0, 0, None, None)
      val unknown2 = Version.Unknown("2.0.0")

      assert(implicitly[Ordering[Version]].compare(semver2, unknown2) == 0)
    }

    "handle mixed version types in a list" in {
      val versions: List[Version] = List(
        Version.Unknown("1.0.0-custom"),
        Version.Semver(1, 0, 0, Some("rc"), None),
        Version.Canonical(NEList(1, 0, 0), List("beta")),
        Version.Semver(1, 0, 0, Some("alpha"), None),
        Version.Unknown("1.0.0"),
        Version.Canonical(NEList(1, 0, 0), List.empty),
      )

      val sorted = versions.sorted
      assert(
        sorted == List(
          Version.Unknown("1.0.0"),
          Version.Semver(1, 0, 0, Some("alpha"), None),
          Version.Canonical(NEList(1, 0, 0), List("beta")),
          Version.Unknown("1.0.0-custom"),
          Version.Semver(1, 0, 0, Some("rc"), None),
          Version.Canonical(NEList(1, 0, 0), List.empty),
        )
      )
    }

    "maintain reflexivity, symmetry, and transitivity" in {
      val v1: Version = Version.Semver(1, 0, 0, None, None)
      val v2: Version = Version.Canonical(NEList(1, 0, 0), List.empty)
      val v3: Version = Version.Unknown("1.0.0")

      // Reflexivity
      assert(implicitly[Ordering[Version]].compare(v1, v1) == 0)
      assert(implicitly[Ordering[Version]].compare(v2, v2) == 0)
      assert(implicitly[Ordering[Version]].compare(v3, v3) == 0)

      // When comparing through conversions, these should be equal
      assert(implicitly[Ordering[Version]].compare(v1, v2) == 0) // Semver converts to same Canonical
      assert(implicitly[Ordering[Version]].compare(v2, v3) == 0) // Both toString to "1.0.0"
      assert(implicitly[Ordering[Version]].compare(v1, v3) == 0) // Semver toString to "1.0.0"
    }
  }
}
