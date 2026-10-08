package izumi.fundamentals.platform.versions

import izumi.fundamentals.collections.nonempty.NEList
import izumi.fundamentals.testkit.AnyWordSpec

class VersionTest extends AnyWordSpec {
  private final case class ParseCase[A](name: String, input: String, expected: A)

  private def checkParsing[A](parse: String => A)(cases: ParseCase[A]*): Unit = {
    cases.foreach { testcase =>
      testcase.name in { assert(parse(testcase.input) == testcase.expected) }
    }
  }

  private def assertSorted[A: Ordering](versions: List[A], expectedOrder: List[Int]): Unit = {
    assert(versions.sorted == expectedOrder.map(versions))
  }

  "Version.parseSemver" should {
    checkParsing(Version.parseSemver)(
      ParseCase("parse basic semantic versions", "1.2.3", Some(Version.Semver(1, 2, 3, None, None))),
      ParseCase("parse semantic versions with pre-release", "1.0.0-alpha", Some(Version.Semver(1, 0, 0, Some("alpha"), None))),
      ParseCase(
        "parse semantic versions with pre-release and build metadata",
        "1.0.0-alpha.1+20130313144700",
        Some(Version.Semver(1, 0, 0, Some("alpha.1"), Some("20130313144700"))),
      ),
      ParseCase("parse semantic versions with build metadata only", "1.0.0+20130313144700", Some(Version.Semver(1, 0, 0, None, Some("20130313144700")))),
      ParseCase("parse complex pre-release identifiers", "1.0.0-rc.1.2.3", Some(Version.Semver(1, 0, 0, Some("rc.1.2.3"), None))),
    )

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
    checkParsing(Version.parseCanonical)(
      ParseCase("parse simple version numbers", "1.2.3", Some(Version.Canonical(NEList(1, 2, 3), List.empty))),
      ParseCase("parse versions with qualifiers", "1.2.3-SNAPSHOT", Some(Version.Canonical(NEList(1, 2, 3), List("SNAPSHOT")))),
      ParseCase("parse versions with multiple qualifiers", "1.2.3-alpha-SNAPSHOT-TEST", Some(Version.Canonical(NEList(1, 2, 3), List("alpha", "SNAPSHOT", "TEST")))),
      ParseCase("parse single component versions", "42", Some(Version.Canonical(NEList(42), List.empty))),
      ParseCase("parse two component versions", "1.0", Some(Version.Canonical(NEList(1, 0), List.empty))),
      ParseCase("parse four or more component versions", "1.2.3.4", Some(Version.Canonical(NEList(1, 2, 3, 4), List.empty))),
      ParseCase("parse versions with qualifiers and multiple components", "1.0-beta-1", Some(Version.Canonical(NEList(1, 0), List("beta", "1")))),
    )

    "return None for invalid formats" in {
      assert(Version.parseCanonical("").isEmpty)
      assert(Version.parseCanonical("a.b.c").isEmpty)
      assert(Version.parseCanonical("1.2.a").isEmpty)
      assert(Version.parseCanonical("-SNAPSHOT").isEmpty)
    }
  }

  "Version.parse" should {
    checkParsing(Version.parse)(
      ParseCase("parse as Semver when possible", "1.2.3", Version.Semver(1, 2, 3, None, None)),
      ParseCase("parse as Semver with pre-release and build", "1.0.0-alpha+build", Version.Semver(1, 0, 0, Some("alpha"), Some("build"))),
      ParseCase("fall back to Canonical for non-semver versions", "1.2", Version.Canonical(NEList(1, 2), List.empty)),
      ParseCase("fall back to Canonical for four component versions", "1.2.3.4", Version.Canonical(NEList(1, 2, 3, 4), List.empty)),
      ParseCase("return Unknown for invalid versions", "not-a-version", Version.Unknown("not-a-version")),
      ParseCase("return Unknown for empty string", "", Version.Unknown("")),
    )

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
      assertSorted(versions, List(1, 3, 2, 0))
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
      assertSorted(versions, List(1, 0, 2))
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
      assertSorted(versions, List(1, 3, 2, 0))
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
      assertSorted(versions, List(1, 0, 2, 3))
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
      assertSorted(versions, List(1, 0, 3, 2))
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
      assertSorted(versions, List(3, 2, 1, 0))
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
      assertSorted(versions, List(4, 3, 2, 0, 1, 5))
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
