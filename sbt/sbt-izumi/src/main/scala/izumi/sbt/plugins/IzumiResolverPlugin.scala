package izumi.sbt.plugins

import sbt.Keys._
import sbt._

object IzumiResolverPlugin extends AutoPlugin {
  import IzumiPropertiesPlugin.autoImport._

  override lazy val globalSettings = Seq(
    aggregate in update := sys.props.getBoolean("build.update.aggregate", default = true),
    updateOptions := updateOptions
      .value
      .withInterProjectFirst(sys.props.getBoolean("build.update.inter-project-first", default = true))
      .withCachedResolution(sys.props.getBoolean("build.update.cached-resolution", default = true))
      .withGigahorse(sys.props.getBoolean("build.update.gigahorse", default = true))
      .withLatestSnapshots(sys.props.getBoolean("build.update.latest-snapshots", default = false))
    ,
  )
}
