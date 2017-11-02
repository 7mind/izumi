package org.bitbucket.pshirshov.izumi.sbt

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import org.bitbucket.pshirshov.izumi.sbt.definitions.Properties._
import sbt.Keys._
import sbt.{AutoPlugin, Package, ThisBuild}
import com.typesafe.sbt.pgp.PgpKeys._
import sbt.internal.util.ConsoleLogger
import sbt.librarymanagement.PublishConfiguration

object PublishingPlugin extends AutoPlugin {
  protected val logger: ConsoleLogger = ConsoleLogger()

  override def trigger = allRequirements

  override lazy val globalSettings = Seq(
    pomIncludeRepository := (_ => false)
    , packageOptions += {
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
      Package.ManifestAttributes(attributes.toSeq :_*)
    }
  )

  override lazy val projectSettings = Seq(
    publishConfiguration ~= withOverwrite
    , publishLocalConfiguration ~= withOverwrite

    , publishSignedConfiguration ~= withOverwrite
    , publishLocalSignedConfiguration ~= withOverwrite
  )

  private def withOverwrite(config: PublishConfiguration) = {
      config.withOverwrite(sys.props.getBoolean("build.publish.overwrite", config.overwrite))
  }
}
