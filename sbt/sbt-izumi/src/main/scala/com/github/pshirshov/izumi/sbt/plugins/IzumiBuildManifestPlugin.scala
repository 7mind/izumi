package com.github.pshirshov.izumi.sbt.plugins

import java.time.ZonedDateTime
import sbt.Keys.{packageOptions, version}
import sbt.internal.util.ConsoleLogger
import sbt.{AutoPlugin, Package, ThisBuild}

object IzumiBuildManifestPlugin extends AutoPlugin {
  protected val logger: ConsoleLogger = ConsoleLogger()

  override def globalSettings = Seq(
    packageOptions += {
      val attributes = Map(
        IzumiManifest.BuiltBy -> System.getProperty("user.name")
        , IzumiManifest.BuildJdk -> System.getProperty("java.version")
        , IzumiManifest.Version -> (version in ThisBuild).value
        , IzumiManifest.BuildTimestamp -> IzumiManifest.TsFormat.format(ZonedDateTime.now())
      )
      attributes.foreach {
        case (k, v) =>
          logger.info(s"Manifest value: $k = $v")
      }
      Package.ManifestAttributes(attributes.toSeq: _*)
    }
  )


}
