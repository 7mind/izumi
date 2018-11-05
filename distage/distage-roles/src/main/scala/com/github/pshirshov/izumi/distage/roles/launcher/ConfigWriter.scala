package com.github.pshirshov.izumi.distage.roles.launcher

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import com.github.pshirshov.izumi.distage.model.definition.Id
import com.github.pshirshov.izumi.distage.roles.launcher.ConfigWriter.WriteReference
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.platform.resources.{ArtifactVersion, IzManifest}
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import com.github.pshirshov.izumi.distage.roles.roles._

import scala.reflect.ClassTag
import scala.util._


object ConfigWriter extends RoleDescriptor {

  case class WriteReference(
                             asJson: Boolean = false,
                             targetDir: String = "config",
                             includeCommon: Boolean = true,
                             useLauncherVersion: Boolean = true,
                           )

  override final val id = "configwriter"
}

case class ConfigurableComponent(componentId: String, version: Option[ArtifactVersion], parent: Option[Config] = None)

@RoleId(ConfigWriter.id)
abstract class AbstractConfigWriter[LAUNCHER: ClassTag]
(
  logger: IzLogger,
  launcherVersion: ArtifactVersion@Id("launcher-version"),
  roleInfo: RolesInfo,
  config: WriteReference,
)
  extends RoleService
    with RoleTask {
  override def start(): Unit = {
    writeReferenceConfig()
  }

  def writeReferenceConfig(): Unit = {
    val configPath = Paths.get(config.targetDir).toFile
    logger.info(s"Config ${configPath.getAbsolutePath -> "directory to use"}...")

    if (!configPath.exists())
      configPath.mkdir()

    val maybeVersion = IzManifest.manifest[LAUNCHER]().map(IzManifest.read).map(_.version)
    logger.info(s"Going to process ${roleInfo.availableRoleBindings.size -> "roles"}")

    val commonComponent = ConfigurableComponent("common", maybeVersion)
    val commonConfig = buildConfig(ConfigurableComponent("common", maybeVersion))
    if (!config.includeCommon) {
      writeConfig(commonComponent, commonConfig)
    }

    val (good, bad) = roleInfo.availableRoleBindings.partition(_.anno.nonEmpty)

    if (bad.nonEmpty) {
      import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
      logger.info(s"Roles to be ${bad.niceList() -> "ignored"}")
    }

    Quirks.discard(for {
      binding <- good
      component = ConfigurableComponent(binding.anno.head, binding.source.map(_.version))
      cfg = buildConfig(component.copy(parent = Some(commonConfig)))
    } yield {
      val version = if (config.useLauncherVersion)
        Some(ArtifactVersion(launcherVersion.version))
      else
        component.version

      writeConfig(component.copy(version = version), cfg)
    })
  }

  private def buildConfig(cmp: ConfigurableComponent): Config = {
    val referenceConfig = s"${cmp.componentId}-reference.conf"
    logger.info(s"[${cmp.componentId}] Resolving $referenceConfig... with ${config.includeCommon -> "shared sections"}")

    val reference = (if (config.includeCommon) {
      cmp.parent
        .map(ConfigFactory.parseResourcesAnySyntax(referenceConfig).withFallback)
        .getOrElse(ConfigFactory.parseResourcesAnySyntax(referenceConfig))
    } else {
      ConfigFactory.parseResourcesAnySyntax(referenceConfig)
    }).resolve()

    if (reference.isEmpty) {
      logger.warn(s"[${cmp.componentId}] Reference config is empty. Sounds stupid.")
    }

    val resolved = ConfigFactory.defaultOverrides()
      .withFallback(reference)
      .resolve()

    val filtered = cleanupEffectiveAppConfig(resolved, reference)
    filtered.checkValid(reference)
    filtered
  }

  private def writeConfig(cmp: ConfigurableComponent, typesafeConfig: Config) = {
    val fileName = referenceFileName(cmp.componentId, cmp.version, config.asJson)
    val target = Paths.get(config.targetDir, fileName)

    Try {
      val cfg = render(typesafeConfig, config.asJson)
      Files.write(target, cfg.getBytes(StandardCharsets.UTF_8))
      logger.info(s"[${cmp.componentId}] Reference config saved -> $target")
    }.recover {
      case e: Throwable => logger.error(s"[${cmp.componentId -> "component id" -> null}] Can't write reference config to $target, ${e.getMessage -> "message"}")
    }
  }

  // TODO: sdk?
  private final def cleanupEffectiveAppConfig(effectiveAppConfig: Config, reference: Config): Config = {
    import scala.collection.JavaConverters._

    ConfigFactory.parseMap(effectiveAppConfig.root().unwrapped().asScala.filterKeys(reference.hasPath).asJava)
  }

  private def render(cfg: Config, asJson: Boolean) = {
    cfg.root().render(configRenderOptions.setJson(asJson))
  }

  private def referenceFileName(service: String, version: Option[ArtifactVersion], asJson: Boolean): String = {
    val extension = if (asJson) {
      "json"
    } else {
      "conf"
    }

    val vstr = version.map(_.version).getOrElse("0.0.0-UNKNOWN")
    s"$service-$vstr.$extension"
  }

  private val configRenderOptions = ConfigRenderOptions.defaults.setOriginComments(false).setComments(false)
}
