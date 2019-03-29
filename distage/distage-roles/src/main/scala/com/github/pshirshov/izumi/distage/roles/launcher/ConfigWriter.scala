package com.github.pshirshov.izumi.distage.roles.launcher

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import com.github.pshirshov.izumi.distage.app.{DIAppStartupContext, StartupContext}
import com.github.pshirshov.izumi.distage.config.ResolvedConfig
import com.github.pshirshov.izumi.distage.model.definition.Id
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.roles._
import com.github.pshirshov.izumi.distage.roles.launcher.ConfigWriter.WriteReference
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.platform.resources.{ArtifactVersion, IzManifest}
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}

import scala.reflect.ClassTag
import scala.util._

object ConfigWriter extends RoleDescriptor {
  override final val id = "configwriter"

  /**
    * Configuration for [[AbstractConfigWriter]]
    *
    * @param includeCommon Append shared sections from `common-reference.conf` into every written config
    */
  case class WriteReference(
                             asJson: Boolean = false,
                             targetDir: String = "config",
                             includeCommon: Boolean = true,
                             useLauncherVersion: Boolean = true,
                           )

}

final case class ConfigurableComponent(
                                        componentId: String
                                      , version: Option[ArtifactVersion]
                                      , parent: Option[Config] = None
                                      )

object AbstractConfigWriter {


}

@RoleId(ConfigWriter.id)
abstract class AbstractConfigWriter[LAUNCHER: ClassTag]
(
  logger: IzLogger,
  launcherVersion: ArtifactVersion @Id("launcher-version"),
  roleInfo: RolesInfo,
  config: WriteReference,
  context: DIAppStartupContext,
)
  extends RoleService
    with RoleTask {

  override def start(): Unit = {
    writeReferenceConfig()
  }

  private[this] def writeReferenceConfig(): Unit = {
    val configPath = Paths.get(config.targetDir).toFile
    logger.info(s"Config ${configPath.getAbsolutePath -> "directory to use"}...")

    if (!configPath.exists())
      configPath.mkdir()

    val maybeVersion = IzManifest.manifest[LAUNCHER]().map(IzManifest.read).map(_.version)
    logger.info(s"Going to process ${roleInfo.availableRoleBindings.size -> "roles"}")

    val commonConfig = buildConfig(ConfigurableComponent("common", maybeVersion))
    if (!config.includeCommon) {
      val commonComponent = ConfigurableComponent("common", maybeVersion)
      writeConfig(commonComponent, None, commonConfig)
    }

    Quirks.discard(for {
      role <- roleInfo.availableRoleBindings
    } yield {
      val component = ConfigurableComponent(role.name, role.source.map(_.version))
      val cfg = buildConfig(component.copy(parent = Some(commonConfig)))

      val version = if (config.useLauncherVersion) {
        Some(ArtifactVersion(launcherVersion.version))
      } else {
        component.version
      }
      val versionedComponent = component.copy(version = version)

      writeConfig(versionedComponent, None, cfg)

      minimizedConfig(context.startupContext, logger)(role)
        .foreach {
          cfg =>
            writeConfig(versionedComponent, Some("minimized"), cfg)
        }
    })
  }

  private[this] def buildConfig(cmp: ConfigurableComponent): Config = {
    val referenceConfig = s"${cmp.componentId}-reference.conf"
    logger.info(s"[${cmp.componentId}] Resolving $referenceConfig... with ${config.includeCommon -> "shared sections"}")

    val reference = cmp.parent
      .filter(_ => config.includeCommon)
      .fold(
        ConfigFactory.parseResourcesAnySyntax(referenceConfig)
      )(parent =>
        ConfigFactory.parseResourcesAnySyntax(referenceConfig).withFallback(parent)
      ).resolve()

    if (reference.isEmpty) {
      logger.warn(s"[${cmp.componentId}] Reference config is empty.")
    }

    val resolved = ConfigFactory.systemProperties()
      .withFallback(reference)
      .resolve()

    val filtered = cleanupEffectiveAppConfig(resolved, reference)
    filtered.checkValid(reference)
    filtered
  }

  private[this] def minimizedConfig(startupContext: StartupContext, logger: IzLogger)(role: RoleBinding): Option[Config] = {
    val roleDIKey = role.binding.key
    val newPlan = startupContext.startup(Array("--root-log-level", "crit", role.name)).plan

    if (newPlan.steps.exists(_.target == roleDIKey)) {
      newPlan
        .filter[ResolvedConfig]
        .collect {
          case op: WiringOp.ReferenceInstance =>
            op.wiring.instance
        }
        .collectFirst {
          case r: ResolvedConfig =>
            r.minimized()
        }
    } else {
      logger.warn(s"$roleDIKey is not in the refined plan")
      None
    }
  }

  private[this] def writeConfig(cmp: ConfigurableComponent, suffix: Option[String], typesafeConfig: Config): Try[Unit] = {
    val fileName = outputFileName(cmp.componentId, cmp.version, config.asJson, suffix)
    val target = Paths.get(config.targetDir, fileName)

    Try {
      val cfg = typesafeConfig.root().render(configRenderOptions.setJson(config.asJson))
      val bytes = cfg.getBytes(StandardCharsets.UTF_8)
      Files.write(target, bytes)
      logger.info(s"[${cmp.componentId}] Reference config saved -> $target (${bytes.size} bytes)")
    }.recover {
      case e: Throwable =>
        logger.error(s"[${cmp.componentId -> "component id" -> null}] Can't write reference config to $target, ${e.getMessage -> "message"}")
    }
  }

  // TODO: sdk?
  private[this] def cleanupEffectiveAppConfig(effectiveAppConfig: Config, reference: Config): Config = {
    import scala.collection.JavaConverters._

    ConfigFactory.parseMap(effectiveAppConfig.root().unwrapped().asScala.filterKeys(reference.hasPath).asJava)
  }

  private[this] def outputFileName(service: String, version: Option[ArtifactVersion], asJson: Boolean, suffix: Option[String]): String = {
    val extension = if (asJson) {
      "json"
    } else {
      "conf"
    }

    val vstr = version.map(_.version).getOrElse("0.0.0-UNKNOWN")
    suffix match {
      case Some(value) =>
        s"$service-$value-$vstr.$extension"
      case None =>
        s"$service-$vstr.$extension"
    }
  }

  private[this] final val configRenderOptions = ConfigRenderOptions.defaults.setOriginComments(false).setComments(false)
}
