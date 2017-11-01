package org.bitbucket.pshirshov.izumi.sbt

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import sbt.Keys._
import sbt.{AutoPlugin, Def, Package}

object PublishingPlugin extends AutoPlugin {
  override lazy val globalSettings = Seq(
    pomIncludeRepository := (_ => false)
    , packageOptions ++= Def.task {
      Seq(
        Package.ManifestAttributes(
          "X-Built-By" -> System.getProperty("user.name")
          , "X-Build-JDK" -> System.getProperty("java.version")
          , "X-Version" -> version.value
          , "X-Build-Timestamp" -> DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now())

        ))
    }.value
  )

}
