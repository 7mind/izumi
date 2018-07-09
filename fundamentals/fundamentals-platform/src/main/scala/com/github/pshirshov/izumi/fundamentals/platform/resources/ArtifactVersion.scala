package com.github.pshirshov.izumi.fundamentals.platform.resources

import java.time.ZonedDateTime

import com.github.pshirshov.izumi.fundamentals.platform.time.IzTime._

case class IzArtifactId(groupId: String, artifactId: String) {
  override def toString: String = s"$groupId:$artifactId"
}

// TODO: full-scale version, with proper parsing & comparators
case class ArtifactVersion(version: String) {
  override def toString: String = version
}

case class BuildStatus(user: String, jdk: String, timestamp: ZonedDateTime) {
  override def toString: String = s"$user@${timestamp.isoFormatUtc}, JDK $jdk"
}

case class GitStatus(branch: String, repoClean: Boolean, revision: String) {
  override def toString: String = {
    val out = s"""$branch#$revision"""
    if (repoClean) {
      out
    } else {
      s"$out*"
    }
  }
}

case class IzArtifact(id: IzArtifactId, version: ArtifactVersion, build: BuildStatus, git: GitStatus) {
  def shortInfo: String = {
    s"$version @ $git, $id, ${build.timestamp.isoFormatUtc}"
  }

  def justVersion: String = {
    s"$version @ $git, ${build.timestamp.isoFormatUtc}"
  }

  override def toString: String = {
    s"$shortInfo (jdk: ${build.jdk}, by: ${build.user})"
  }
}
