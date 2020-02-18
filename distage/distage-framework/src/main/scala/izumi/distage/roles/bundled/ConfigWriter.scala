package izumi.distage.roles.bundled

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import izumi.distage.config.extractor.ConfigPathExtractor.ResolvedConfig
import izumi.distage.config.extractor.ConfigPathExtractorModule
import izumi.distage.framework.services.RoleAppPlanner
import izumi.distage.model.definition.Id
import izumi.distage.model.effect.DIEffect
import izumi.distage.model.plan.ExecutableOp.WiringOp
import izumi.distage.model.plan.OrderedPlan
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.SingletonWiring.Instance
import izumi.distage.roles.bundled.ConfigWriter.{ConfigurableComponent, WriteReference}
import izumi.distage.roles.model.meta.{RoleBinding, RolesInfo}
import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams
import izumi.fundamentals.platform.cli.model.schema.{ParserDef, RoleParserSchema}
import izumi.fundamentals.platform.language.unused
import izumi.fundamentals.platform.resources.ArtifactVersion
import izumi.logstage.api.IzLogger
import izumi.logstage.api.logger.LogRouter
import izumi.logstage.distage.LogstageModule

import scala.util._


class ConfigWriter[F[_] : DIEffect]
(
  logger: IzLogger,
  launcherVersion: ArtifactVersion@Id("launcher-version"),
  roleInfo: RolesInfo,
  context: RoleAppPlanner[F],
  //appModule: ModuleBase@Id("application.module"),
) extends RoleTask[F] {

  override def start(roleParameters: RawEntrypointParams, @unused freeArgs: Vector[String]): F[Unit] = {
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

    roleInfo.availableRoleBindings.foreach { role =>
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
    }
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
      new ConfigPathExtractorModule,
      new LogstageModule(LogRouter.nullRouter, setupStaticLogRouter = false),
    ).overrideLeft

    //val newAppModule = appModule
    val plans = context.reboot(cfg).makePlan(Set(roleDIKey))

    def getConfig(plan: OrderedPlan): Option[Config] = {
      plan
        .filter[ResolvedConfig]
        .collectFirst {
          case WiringOp.UseInstance(_, Instance(_, r: ResolvedConfig), _) =>
            r.minimized(config)
        }
    }

    def getConfigOrEmpty(plan: OrderedPlan) = {
      getConfig(plan) getOrElse ConfigFactory.empty()
    }

    if (plans.app.primary.steps.exists(_.target == roleDIKey)) {
      val cfg = getConfig(plans.app.primary)
        .orElse(getConfig(plans.app.shared))
        .orElse(getConfig(plans.app.side))

      cfg
        .map(_.withFallback(getConfigOrEmpty(plans.app.side)))
        .map(_.withFallback(getConfigOrEmpty(plans.app.shared)))
        .map(_.withFallback(getConfigOrEmpty(plans.runtime)))
        .orElse {
          logger.error(s"Couldn't produce minimized config for $roleDIKey")
          None
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
    import scala.jdk.CollectionConverters._

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
    RoleParserSchema(id, Options, Some("Dump reference configs for all the roles"), None, freeArgsAllowed = false)
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
                                          componentId: String,
                                          version: Option[ArtifactVersion],
                                          parent: Option[Config] = None,
                                        )

  object Options extends ParserDef {
    final val targetDir = arg("target", "t", "target directory", "<path>")
    final val excludeCommon = flag("exclude-common", "ec", "do not include shared sections")
    final val useComponentVersion = flag("version-use-component", "vc", "use component version instead of launcher version")
    final val formatTypesafe = arg("format", "f", "output format, json is default", "{json|hocon}")
  }

  def parse(p: RawEntrypointParams): WriteReference = {
    val targetDir = p.findValue(Options.targetDir).map(_.value).getOrElse("config")
    val includeCommon = p.hasNoFlag(Options.excludeCommon)
    val useLauncherVersion = p.hasNoFlag(Options.useComponentVersion)
    val asJson = !p.findValue(Options.formatTypesafe).map(_.value).contains("hocon")

    WriteReference(
      asJson,
      targetDir,
      includeCommon,
      useLauncherVersion,
    )
  }

}
