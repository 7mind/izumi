package com.github.pshirshov.izumi.distage.roles.internal

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.config.{ConfigModule, ResolvedConfig}
import com.github.pshirshov.izumi.distage.model.definition.{Id, ModuleBase}
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.plan.OrderedPlan
import com.github.pshirshov.izumi.distage.roles.internal.ConfigWriter.{ConfigurableComponent, WriteReference}
import com.github.pshirshov.izumi.distage.roles.model.meta.{RoleBinding, RolesInfo}
import com.github.pshirshov.izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import com.github.pshirshov.izumi.distage.roles.services.ModuleProviderImpl.ContextOptions
import com.github.pshirshov.izumi.distage.roles.services.RoleAppPlanner
import com.github.pshirshov.izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams
import com.github.pshirshov.izumi.fundamentals.platform.cli.model.schema.{ParserDef, RoleParserSchema}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.platform.resources.ArtifactVersion
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import com.github.pshirshov.izumi.logstage.distage.LogstageModule
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}

import scala.util._

class ConfigWriter[F[_] : DIEffect]
(
  logger: IzLogger,
  launcherVersion: ArtifactVersion@Id("launcher-version"),
  roleInfo: RolesInfo,
  context: RoleAppPlanner[F],
  options: ContextOptions,
  appModule: ModuleBase@Id("application.module")
)
  extends RoleTask[F] {

  override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): F[Unit] = {
    Quirks.discard(freeArgs)
    val config = ConfigWriter.parse(roleParameters)
    DIEffect[F].maybeSuspend(writeReferenceConfig(config))
  }

  private[this] def writeReferenceConfig(options: WriteReference): Unit = {
    val configPath = Paths.get(options.targetDir).toFile
    logger.info(s"Config ${configPath.getAbsolutePath -> "directory to use"}...")

    if (!configPath.exists())
      configPath.mkdir()

    //val maybeVersion = IzManifest.manifest[LAUNCHER]().map(IzManifest.read).map(_.version)
    logger.info(s"Going to process ${roleInfo.availableRoleBindings.size -> "roles"}")

    val commonConfig = buildConfig(options, ConfigurableComponent("common", Some(launcherVersion)))
    if (!options.includeCommon) {
      val commonComponent = ConfigurableComponent("common", Some(launcherVersion))
      writeConfig(options, commonComponent, None, commonConfig)
    }

    Quirks.discard(for {
      role <- roleInfo.availableRoleBindings
    } yield {
      val component = ConfigurableComponent(role.descriptor.id, role.source.map(_.version))
      val refConfig = buildConfig(options, component.copy(parent = Some(commonConfig)))
      val version = if (options.useLauncherVersion) {
        Some(ArtifactVersion(launcherVersion.version))
      } else {
        component.version
      }
      val versionedComponent = component.copy(version = version)

      try {
        writeConfig(options, versionedComponent, None, refConfig)

        minimizedConfig(refConfig, role)
          .foreach {
            cfg =>
              writeConfig(options, versionedComponent, Some("minimized"), cfg)
          }
      } catch {
        case exception: Throwable =>
          logger.crit(s"Cannot process role ${role.descriptor.id}")
          throw exception
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

  private[this] def minimizedConfig(config: Config, role: RoleBinding): Option[Config] = {
    val roleDIKey = role.binding.key

    val cfg = Seq(
      new ConfigModule(AppConfig(config), options.configInjectionOptions),
      new LogstageModule(LogRouter.nullRouter, false),
    ).overrideLeft

    val plans = context.reboot(cfg).makePlan(Set(roleDIKey), appModule)

    def getConfig(plan: OrderedPlan): Option[Config] = {
      plan
        .filter[ResolvedConfig]
        .collect {
          case op: WiringOp.ReferenceInstance =>
            op.wiring.instance
        }
        .collectFirst {
          case r: ResolvedConfig =>
            r.minimized()
        }
    }

    def getConfigOrEmpty(plan: OrderedPlan) = {
      getConfig(plan) getOrElse ConfigFactory.empty()
    }

    if (plans.app.steps.exists(_.target == roleDIKey)) {
      getConfig(plans.app)
        .map(_.withFallback(getConfigOrEmpty(plans.integration)))
        .map(_.withFallback(getConfigOrEmpty(plans.runtime)))
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

    ConfigFactory.parseMap(effectiveAppConfig.root().unwrapped().asScala.filterKeys(reference.hasPath).toMap.asJava)
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


object ConfigWriter extends RoleDescriptor {
  override final val id = "configwriter"

  override def parserSchema: RoleParserSchema = {
    RoleParserSchema(id, P, Some("Dump reference configs for all the roles"), None, freeArgsAllowed = false)
  }

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


  object P extends ParserDef {
    final val targetDir = arg("target", "t", "target directory", "<path>")
    final val excludeCommon = flag("exclude-common", "ec", "do not include shared sections")
    final val useComponentVersion = flag("version-use-component", "vc", "use component version instead of launcher version")
    final val formatTypesafe = arg("format", "f", "output format, json is default", "{json|hocon}")
  }

  def parse(p: RawEntrypointParams): WriteReference = {
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
