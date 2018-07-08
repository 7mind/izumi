package com.github.pshirshov.izumi.fundamentals.platform.resources

import java.time.ZonedDateTime

import com.github.pshirshov.izumi.fundamentals.platform.time.IzTime

case class ArtifactVersion(version: String) // TODO: full-scale version, with proper parsing & comparators

case class BuildStatus(buildBy: String, buildJdk: String, buildTimestamp: ZonedDateTime)

case class IzArtifactId(groupId: String, artifactId: String)

case class GitStatus(branch: Option[String], repoClean: Boolean, revision: Option[String]) {
  override def toString: String = {
    val out = s"""${branch.getOrElse("?")}#$revision"""
    if (repoClean) {
      out
    } else {
      s"$out*"
    }
  }
}

case class IzArtifact(id: IzArtifactId, version: ArtifactVersion, build: BuildStatus, gitStatus: GitStatus) {
  def shortInfo: String = {
    import IzTime._
    s"$version@${build.buildTimestamp.isoFormatUtc}, $gitStatus"
  }
}

case class ManifestData(git: Option[GitStatus], app: Option[ArtifactVersion], buildStatus: Option[BuildStatus])
