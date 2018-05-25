package com.github.pshirshov.izumi.sbt

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import com.typesafe.sbt.pgp.PgpKeys._
import laughedelic.sbt.PublishMore
import sbt.Keys.{credentials, _}
import sbt.internal.util.ConsoleLogger
import sbt.io.syntax.File
import sbt.librarymanagement.PublishConfiguration
import sbt.sbtpgp.Compat.publishSignedConfigurationTask
import sbt.{AutoPlugin, Credentials, MavenRepository, Package, ThisBuild, _}

object PublishingPlugin extends AutoPlugin {

  override def requires = super.requires && PublishMore

  case class MavenTarget(id: String, credentials: Credentials, repo: MavenRepository)

  object PublishingPluginKeys {
    lazy val sonatypeTarget = settingKey[MavenRepository]("Sonatype repository based on isSnapshot value")
    lazy val publishTargets = settingKey[Seq[MavenTarget]]("Publishing target")
  }

  import PublishingPluginKeys._

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
      Package.ManifestAttributes(attributes.toSeq: _*)
    }
    , publishTargets := Seq.empty
  )

  import laughedelic.sbt.PublishMore.autoImport._

  override lazy val projectSettings = Seq(
    publishConfiguration := withOverwrite(publishConfiguration.value, isSnapshot.value)
    , publishSignedConfiguration := withOverwrite(publishSignedConfigurationTask.value, isSnapshot.value)
    , publishLocalConfiguration ~= withOverwriteEnabled
    , publishLocalSignedConfiguration ~= withOverwriteEnabled
    , sonatypeTarget := {
      if (isSnapshot.value)
        Opts.resolver.sonatypeSnapshots
      else
        Opts.resolver.sonatypeStaging
    }
    , credentials ++= publishTargets.value.map(_.credentials)
    , publishResolvers ++= publishTargets.value.map(_.repo)
  )

  private def withOverwriteEnabled(config: PublishConfiguration) = {
    config.withOverwrite(true)
  }

  private def withOverwrite(config: PublishConfiguration, isSnapshot: Boolean) = {
    import IzumiPropertiesPlugin.autoImport._
    val doOverwrite = sys.props.getBoolean("build.publish.overwrite", config.overwrite)
    // in case overwrite is already enabled (snapshots, smth else) we should not disable it
    config.withOverwrite(doOverwrite || config.overwrite || isSnapshot)
  }


  object autoImport {
    final val PublishingPluginKeys = PublishingPlugin.PublishingPluginKeys

    object PublishTarget {
      def filter(targets: Option[MavenTarget]*): Seq[MavenTarget] = {
        targets.flatMap(_.toSeq)
      }

      def env(prefix: String): Option[MavenTarget] = {
        val props = List(
          Option(System.getProperty(s"${prefix}_USER"))
          , Option(System.getProperty(s"${prefix}_PASSWORD"))
          , Option(System.getProperty(s"${prefix}_REALM_NAME"))
          , Option(System.getProperty(s"${prefix}_REALM"))
          , Option(System.getProperty(s"${prefix}_URL"))
        )

        props match {
          case Some(user) :: Some(password) :: Some(realmname) :: Some(realm) :: Some(url) :: Nil =>
            import sbt.librarymanagement.syntax._
            Some(MavenTarget(realm, Credentials(realmname, realm, user, password), realm at url))

          case _ =>
            None
        }
      }

      def file(realm: String, url: String, path: File): Option[MavenTarget] = {
        if (path.exists()) {
          import sbt.librarymanagement.syntax._
          Some(MavenTarget(realm, Credentials(path), realm at url))
        } else {
          None
        }
      }
    }



  }

}
