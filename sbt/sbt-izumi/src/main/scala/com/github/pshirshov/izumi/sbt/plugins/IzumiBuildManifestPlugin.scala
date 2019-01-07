package com.github.pshirshov.izumi.sbt.plugins

import java.time.ZonedDateTime

import sbt.Keys._
import sbt.internal.util.ConsoleLogger
import sbt.{AutoPlugin, Def, Package, ThisBuild}

object IzumiBuildManifestPlugin extends AutoPlugin {
  protected val logger: ConsoleLogger = ConsoleLogger()


  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    packageOptions += {
      val attributes = Map(
        IzumiManifest.BuiltBy -> System.getProperty("user.name")
        , IzumiManifest.BuildJdk -> System.getProperty("java.version")
        , IzumiManifest.Version -> version.value
        , IzumiManifest.BuildSbt -> sbtVersion.value
        , IzumiManifest.BuildScala -> scalaVersion.value
        , IzumiManifest.BuildTimestamp -> IzumiManifest.TsFormat.format(ZonedDateTime.now())
      )

      attributes.foreach {
        case (k, v) =>
          logger.debug(s"Manifest value: $k = $v")
      }

      Package.ManifestAttributes(attributes.toSeq: _*)
    }
  )

}
