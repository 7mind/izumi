package com.github.pshirshov.izumi.sbt.plugins

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import sbt.Keys.{packageOptions, version}
import sbt.internal.util.ConsoleLogger
import sbt.{AutoPlugin, Package, ThisBuild}

object IzumiBuildManifestPlugin extends AutoPlugin {
  protected val logger: ConsoleLogger = ConsoleLogger()

  override def globalSettings = Seq(
    packageOptions += {
      val attributes = Map(
        "X-Built-By" -> System.getProperty("user.name")
        , "X-Build-JDK" -> System.getProperty("java.version")
        , "X-Version" -> (version in ThisBuild).value
        , "X-Build-Timestamp" -> DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now())
      )
      attributes.foreach {
        case (k, v) =>
          logger.info(s"Manifest value: $k = $v")
      }
      Package.ManifestAttributes(attributes.toSeq: _*)
    }
  )


}
