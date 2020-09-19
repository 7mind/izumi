package izumi.fundamentals.platform.resources

import java.time.{Instant, LocalDateTime}

import izumi.fundamentals.platform.time.IzTime
import izumi.fundamentals.platform.time.IzTime._

case class IzArtifactId(groupId: String, artifactId: String) {
  override def toString: String = s"$groupId:$artifactId"
}

// TODO: full-scale version, with proper parsing & comparators
case class ArtifactVersion(version: String) {
  override def toString: String = version
}

case class BuildStatus(user: String, jdk: String, sbt: String, timestamp: LocalDateTime) {
  override def toString: String = s"$user@${timestamp.isoFormat}, JDK $jdk, SBT $sbt"
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
    s"$version @ $git, $id, ${build.timestamp.isoFormat}"
  }

  def justVersion: String = {
    s"$version @ $git, ${build.timestamp.isoFormat}"
  }

  override def toString: String = {
    s"$shortInfo (jdk: ${build.jdk}, by: ${build.user})"
  }
}

object IzArtifact {
  val UNDEFINED = "UNDEFINED"
  def undefined: IzArtifact = IzArtifact(
    IzArtifactId(UNDEFINED, UNDEFINED),
    ArtifactVersion(UNDEFINED),
    BuildStatus(UNDEFINED, UNDEFINED, UNDEFINED, LocalDateTime.ofInstant(Instant.EPOCH, IzTime.TZ_UTC)),
    GitStatus(UNDEFINED, repoClean = false, UNDEFINED),
  )
}
