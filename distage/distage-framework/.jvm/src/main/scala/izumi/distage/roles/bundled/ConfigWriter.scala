package izumi.distage.roles.bundled

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import distage.config.AppConfig
import distage.{BootstrapModuleDef, Plan}
import izumi.distage.config.model.ConfTag
import izumi.distage.framework.services.{ConfigMerger, RoleAppPlanner}
import izumi.distage.model.definition.Id
import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.roles.bundled.ConfigWriter.{ConfigPath, ExtractConfigPath, WriteReference}
import izumi.distage.roles.model.meta.{RoleBinding, RolesInfo}
import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams
import izumi.fundamentals.platform.cli.model.schema.{ParserDef, RoleParserSchema}
import izumi.fundamentals.platform.resources.ArtifactVersion
import izumi.logstage.api.IzLogger
import izumi.logstage.api.logger.LogRouter
import izumi.logstage.distage.LogstageModule

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import scala.annotation.{nowarn, unused}
import scala.collection.compat.immutable.ArraySeq
import scala.util.Try

final class ConfigWriter[F[_]](
  logger: IzLogger,
  launcherVersion: ArtifactVersion @Id("launcher-version"),
  roleInfo: RolesInfo,
  roleAppPlanner: RoleAppPlanner,
  appConfig: AppConfig,
  F: QuasiIO[F],
) extends RoleTask[F]
  with BundledTask {

  // fixme: always include `activation` section in configs (Used in RoleAppLauncherImpl#configActivationSection, but not seen in config bindings, since it's not read by DI)
  //  should've been unnecessary after https://github.com/7mind/izumi/issues/779
  //  but, the contents of the MainAppModule (including `"activation"` config read) are not accessible here from `RoleAppPlanner` yet...
  private[this] val _HackyMandatorySection = ConfigPath("activation")
  private val configMerger = new ConfigMerger.ConfigMergerImpl(logger)

  override def start(roleParameters: RawEntrypointParams, @unused freeArgs: Vector[String]): F[Unit] = {
    F.maybeSuspend {
      val config = ConfigWriter.parse(roleParameters)
      writeReferenceConfig(config)
    }
  }

  private[this] def writeReferenceConfig(options: WriteReference): Unit = {
    val configPath = Paths.get(options.targetDir).toFile
    logger.info(s"Config ${configPath.getAbsolutePath -> "directory to use"}...")

    if (!configPath.exists()) {
      configPath.mkdir()
    }

    logger.info(s"Going to process ${roleInfo.availableRoleBindings.size -> "roles"}")

    val index = appConfig.roles.map(c => (c.roleConfig.role, c)).toMap
    assert(roleInfo.availableRoleNames == index.keySet)

    roleInfo.availableRoleBindings.foreach {
      role =>
        try {
          val roleId = role.descriptor.id
          val roleVersion = if (options.useLauncherVersion) {
            Some(launcherVersion.version)
          } else {
            role.descriptor.artifact.map(_.version).map(_.version)
          }
          val subLogger = logger("role" -> roleId)
          val fileNameFull = outputFileName(roleId, roleVersion, options.asJson, Some("full"))

          val loaded = index(roleId)

          // TODO: mergeFilter considers system properties, we might want to AVOID that in configwriter
          val mergedRoleConfig = configMerger.mergeFilter(appConfig.shared, List(loaded), _ => true)
          writeConfig(options, fileNameFull, mergedRoleConfig, subLogger)

          minimizedConfig(mergedRoleConfig, role)
            .foreach {
              cfg =>
                val fileNameMinimized = outputFileName(roleId, roleVersion, options.asJson, Some("minimized"))
                writeConfig(options, fileNameMinimized, cfg, subLogger)
            }
        } catch {
          case exception: Throwable =>
            logger.crit(s"Cannot process role ${role.descriptor.id}")
            throw exception
        }
    }
  }

  private[this] def outputFileName(service: String, version: Option[String], asJson: Boolean, suffix: Option[String]): String = {
    val extension = if (asJson) "json" else "conf"
    val vstr = version.getOrElse("0.0.0-UNKNOWN")
    val suffixStr = suffix.fold("")("-" + _)

    s"$service$suffixStr-$vstr.$extension"
  }

  private[this] def minimizedConfig(roleConfig: Config, role: RoleBinding): Option[Config] = {
    val roleDIKey = role.binding.key

    val roleConfigs = appConfig.roles.map(lrc => lrc.copy(roleConfig = lrc.roleConfig.copy(active = lrc.roleConfig.role == role.descriptor.id)))

    // TODO: mergeFilter considers system properties, we might want to AVOID that in configwriter
    // TODO: here we accept all the role configs regardless of them being active or not, that might resolve cross-role conflicts in unpredictable manner
    val fullConfig = configMerger.mergeFilter(appConfig.shared, roleConfigs, _ => true)
    val correctedAppConfig = appConfig.copy(config = fullConfig, roles = roleConfigs)

    val bootstrapOverride = new BootstrapModuleDef {
      include(new LogstageModule(LogRouter.nullRouter, setupStaticLogRouter = false))
    }

    val plans = roleAppPlanner
      .reboot(bootstrapOverride, Some(correctedAppConfig))
      .makePlan(Set(roleDIKey))

    def getConfig(plan: Plan): Iterator[ConfigPath] = {
      plan.stepsUnordered.iterator.collect {
        case ExtractConfigPath(path) => path
      }
    }

    val resolvedConfig =
      getConfig(plans.app).toSet + _HackyMandatorySection

    if (plans.app.stepsUnordered.exists(_.target == roleDIKey)) {
      Some(ConfigWriter.minimized(resolvedConfig, roleConfig))
    } else {
      logger.warn(s"$roleDIKey is not in the refined plan")
      None
    }
  }

  //  private[this] def buildConfig(config: WriteReference, cmp: ConfigurableComponent): Config = {
  //    val referenceConfig = s"${cmp.roleId}-reference.conf"
  //    logger.info(s"[${cmp.roleId}] Resolving $referenceConfig... with ${config.includeCommon -> "shared sections"}")
  //
  //    val reference = Value(ConfigFactory.parseResourcesAnySyntax(referenceConfig))
  //      .mut(cmp.parent.filter(_ => config.includeCommon))(_.withFallback(_))
  //      .get
  //      .resolve()
  //
  //    if (reference.isEmpty) {
  //      logger.warn(s"[${cmp.roleId}] Reference config is empty.")
  //    }
  //
  //    val resolved = ConfigFactory
  //      .systemProperties()
  //      .withFallback(reference)
  //      .resolve()
  //
  //    val filtered = cleanupEffectiveAppConfig(resolved, reference)
  //    filtered.checkValid(reference)
  //    filtered
  //  }
  //
  //
  //
  //  // TODO: sdk?
  //  @nowarn("msg=Unused import")
  //  private[this] def cleanupEffectiveAppConfig(effectiveAppConfig: Config, reference: Config): Config = {
  //    import scala.collection.compat._
  //    import scala.jdk.CollectionConverters._
  //
  //    ConfigFactory.parseMap(effectiveAppConfig.root().unwrapped().asScala.view.filterKeys(reference.hasPath).toMap.asJava)
  //  }
  //
  //  private[this] def outputFileName(service: String, version: Option[ArtifactVersion], asJson: Boolean, suffix: Option[String]): String = {
  //    val extension = if (asJson) "json" else "conf"
  //    val vstr = version.map(_.version).getOrElse("0.0.0-UNKNOWN")
  //    val suffixStr = suffix.fold("")("-" + _)
  //
  //    s"$service$suffixStr-$vstr.$extension"
  //  }
  //

  private[this] def writeConfig(options: WriteReference, fileName: String, typesafeConfig: Config, subLogger: IzLogger): Try[Unit] = {
    val configRenderOptions = ConfigRenderOptions.defaults.setOriginComments(false).setComments(false)

    val target = Paths.get(options.targetDir, fileName)
    Try {
      val cfg = typesafeConfig.root().render(configRenderOptions.setJson(options.asJson))
      val bytes = cfg.getBytes(StandardCharsets.UTF_8)
      Files.write(target, bytes)
      subLogger.info(s"Reference config saved -> $target (${bytes.size} bytes)")
    }.recover {
      case error: Throwable =>
        subLogger.error(s"Can't write reference config to $target, $error")
    }
  }
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
    roleId: String,
    version: Option[ArtifactVersion],
    parent: Option[Config],
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

  @nowarn("msg=Unused import")
  def minimized(requiredPaths: Set[ConfigPath], source: Config): Config = {
    import scala.collection.compat.*
    import scala.jdk.CollectionConverters.*

    val paths = requiredPaths.map(_.toPath)

    ConfigFactory.parseMap {
      source
        .root().unwrapped().asScala
        .view
        .filterKeys(key => paths.exists(_.startsWith(key)))
        .toMap
        .asJava
    }
  }

  object ExtractConfigPath {
    def unapply(op: ExecutableOp): Option[ConfigPath] = {
      op.origin.value match {
        case defined: OperationOrigin.Defined =>
          defined.binding.tags.collectFirst {
            case ConfTag(path) => ConfigPath(path)
          }
        case _ =>
          None
      }
    }
  }

  final case class ConfigPath(parts: Seq[String]) {
    def toPath: String = parts.mkString(".")
  }
  object ConfigPath {
    def apply(path: String): ConfigPath = new ConfigPath(ArraySeq.unsafeWrapArray(path.split('.')))
  }

}
