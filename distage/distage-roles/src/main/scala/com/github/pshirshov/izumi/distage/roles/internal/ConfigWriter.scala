package com.github.pshirshov.izumi.distage.roles.internal

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import com.github.pshirshov.izumi.distage.config.ResolvedConfig
import com.github.pshirshov.izumi.distage.model.definition.Id
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.roles.internal.ConfigWriter.{ConfigurableComponent, WriteReference}
import com.github.pshirshov.izumi.distage.roles.model.{RoleBinding, RoleDescriptor, RoleId, RoleTask2, RolesInfo}
import com.github.pshirshov.izumi.distage.roles.services.RoleAppPlanner
import com.github.pshirshov.izumi.fundamentals.platform.cli.CLIParser.{ArgDef, ArgNameDef}
import com.github.pshirshov.izumi.fundamentals.platform.cli.Parameters
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.platform.resources.ArtifactVersion
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}

import scala.util._

object ConfigWriter extends RoleDescriptor {
  override final val id = "configwriter"

  /**
    * Configuration for [[ConfigWriter]]
    *
    * @param includeCommon Append shared sections from `common-reference.conf` into every written config
    */
  case class WriteReference(
                             asJson: Boolean,
                             targetDir: String,
                             includeCommon: Boolean,
                             useLauncherVersion: Boolean,
                           )

  final case class ConfigurableComponent(
                                          componentId: String
                                          , version: Option[ArtifactVersion]
                                          , parent: Option[Config] = None
                                        )


  object P {
    final val targetDir = ArgDef(ArgNameDef("t", "target"))
    final val excludeCommon = ArgDef(ArgNameDef("ec", "exclude-common"))
    final val useComponentVersion = ArgDef(ArgNameDef("vc", "version-use-component"))
    final val overlayVersionFile = ArgDef(ArgNameDef("v", "overlay-version"))
    final val formatTypesafe = ArgDef(ArgNameDef("f", "format"))
  }

  def parse(p: Parameters): WriteReference = {
    val targetDir = P.targetDir.findValue(p).map(_.value).getOrElse("config")
    val includeCommon = !P.excludeCommon.hasFlag(p)
    val useLauncherVersion = !P.useComponentVersion.hasFlag(p)
    val asJson = !P.formatTypesafe.findValue(p).map(_.value).contains("hocon")

    WriteReference(
      asJson,
      targetDir,
      includeCommon,
      useLauncherVersion,
    )
  }

}


@RoleId(ConfigWriter.id)
class ConfigWriter[F[_] : DIEffect]
(
  logger: IzLogger,
  launcherVersion: ArtifactVersion @Id("launcher-version"),
  roleInfo: RolesInfo,
  context: RoleAppPlanner[F],
)
  extends RoleTask2[F] {

  override def start(roleParameters: Parameters, freeArgs: Vector[String]): F[Unit] = {
    Quirks.discard(freeArgs)
    val config = ConfigWriter.parse(roleParameters)
    DIEffect[F].maybeSuspend(writeReferenceConfig(config))
  }

  private[this] def writeReferenceConfig(config: WriteReference): Unit = {
    val configPath = Paths.get(config.targetDir).toFile
    logger.info(s"Config ${configPath.getAbsolutePath -> "directory to use"}...")

    if (!configPath.exists())
      configPath.mkdir()

    //val maybeVersion = IzManifest.manifest[LAUNCHER]().map(IzManifest.read).map(_.version)
    logger.info(s"Going to process ${roleInfo.availableRoleBindings.size -> "roles"}")

    val commonConfig = buildConfig(config, ConfigurableComponent("common", Some(launcherVersion)))
    if (!config.includeCommon) {
      val commonComponent = ConfigurableComponent("common", Some(launcherVersion))
      writeConfig(config, commonComponent, None, commonConfig)
    }

    Quirks.discard(for {
      role <- roleInfo.availableRoleBindings
    } yield {
      val component = ConfigurableComponent(role.name, role.source.map(_.version))
      val cfg = buildConfig(config, component.copy(parent = Some(commonConfig)))

      val version = if (config.useLauncherVersion) {
        Some(ArtifactVersion(launcherVersion.version))
      } else {
        component.version
      }
      val versionedComponent = component.copy(version = version)

      writeConfig(config, versionedComponent, None, cfg)

      minimizedConfig(logger)(role)
        .foreach {
          cfg =>
            writeConfig(config, versionedComponent, Some("minimized"), cfg)
        }
    })
  }

  private[this] def buildConfig(config: WriteReference, cmp: ConfigurableComponent): Config = {
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

  private[this] def minimizedConfig(logger: IzLogger)(role: RoleBinding): Option[Config] = {
    val roleDIKey = role.binding.key
    val newPlan = context.makePlan(Set(role.binding.key)).app //context.startupContext.startup(Array("--root-log-level", "crit", role.name)).plan

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

  private[this] def writeConfig(config: WriteReference, cmp: ConfigurableComponent, suffix: Option[String], typesafeConfig: Config): Try[Unit] = {
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
