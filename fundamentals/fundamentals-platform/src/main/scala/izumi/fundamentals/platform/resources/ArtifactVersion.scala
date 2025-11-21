package izumi.fundamentals.platform.resources

import izumi.fundamentals.platform.versions.Version

case class ArtifactVersion(version: Version) {
  override def toString: String = version.toString
}
