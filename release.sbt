// Release branch overrides for `sbt "release with-defaults"`.
//
// This file is specific to the release/1.2 branch and should NOT be merged into develop.
// On develop, versions use -SNAPSHOT qualifiers (e.g. 1.2.21-SNAPSHOT), so sbt-release
// defaults work correctly: strip qualifier for release, bump minor + SNAPSHOT for next.
//
// On this branch, versions are bare (e.g. 1.2.24), so we need:
//  - releaseVersion: bump patch (1.2.24 → 1.2.25) instead of the default no-op strip
//  - releaseNextVersion: keep the release version as-is instead of jumping to 1.3.0-SNAPSHOT

releaseVersion := { ver =>
  sbtrelease.Version(ver)
    .map(_.withoutQualifier.bump(sbtrelease.Version.Bump.Bugfix).string)
    .getOrElse(sys.error(s"Cannot parse version: $ver"))
}

releaseNextVersion := { ver => ver }
