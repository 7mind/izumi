package izumi.sbt.plugins

import java.time.ZonedDateTime

import sbt.Keys._
import sbt.internal.util.ConsoleLogger
import sbt.{AutoPlugin, Compile, Def, Package, PackageOption, PluginTrigger, taskKey}

object IzumiBuildManifestPlugin extends AutoPlugin {
  protected val logger: ConsoleLogger = ConsoleLogger()

  override def trigger: PluginTrigger = allRequirements

  object Keys {
    val extendedManifestMfAttributes = taskKey[Seq[PackageOption]]("Extended manifest attributes")
  }

  import Keys._


  override def globalSettings: Seq[Def.Setting[_]] = Seq()

  override def projectSettings: Seq[Def.Setting[_]] =
    Seq(
      extendedManifestMfAttributes := Def.task {
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

        Seq(Package.ManifestAttributes(attributes.toSeq: _*))
      }.value,

      packageOptions in packageBin ++= extendedManifestMfAttributes.value,

      packageOptions in Compile ++= extendedManifestMfAttributes.value,
    )

}
