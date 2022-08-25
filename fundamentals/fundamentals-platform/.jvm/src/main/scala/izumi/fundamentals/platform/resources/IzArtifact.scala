package izumi.fundamentals.platform.resources

import izumi.fundamentals.platform.time.IzTime
import izumi.fundamentals.platform.time.IzTime.*

import java.time.{Instant, LocalDateTime}

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
