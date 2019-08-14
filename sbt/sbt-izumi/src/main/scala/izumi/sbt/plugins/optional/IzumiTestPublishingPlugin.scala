package izumi.sbt.plugins.optional

import sbt.Keys.{packageBin, packageDoc, packageSrc, publishArtifact}
import sbt.{AutoPlugin, Test}

object IzumiTestPublishingPlugin extends AutoPlugin {
  override lazy val globalSettings = Seq(
    publishArtifact in(Test, packageBin) := true
    , publishArtifact in(Test, packageDoc) := true
    , publishArtifact in(Test, packageSrc) := true
  )
}
