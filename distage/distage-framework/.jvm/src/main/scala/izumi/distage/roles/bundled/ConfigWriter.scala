package izumi.distage.roles.bundled

import com.typesafe.config.{Config, ConfigObject, ConfigRenderOptions}
import distage.TagK
import distage.config.AppConfig
import izumi.distage.config.codec.ConfigMeta
import izumi.distage.config.model.ConfTag
import izumi.distage.framework.services.{ConfigMerger, RoleAppPlanner}
import izumi.distage.model.definition.{Binding, Id}
import izumi.distage.model.plan.Roots
import izumi.distage.model.planning.AxisPoint
import izumi.distage.planning.solver.PlanVerifier
import izumi.distage.roles.bundled.ConfigWriter.{ConfigPath, WriteReference}
import izumi.distage.roles.model.meta.{RoleBinding, RolesInfo}
import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.collections.nonempty.NESet
import izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams
import izumi.fundamentals.platform.cli.model.schema.{ParserDef, RoleParserSchema}
import izumi.fundamentals.platform.resources.ArtifactVersion
import izumi.fundamentals.platform.strings.IzString.toRichIterable
import izumi.logstage.api.IzLogger

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import scala.annotation.{nowarn, unused}
import scala.collection.compat.immutable.ArraySeq
import scala.util.Try

final class ConfigWriter[F[_]: TagK](
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
  private[this] val _HackyMandatorySection = ConfigPath("activation", wildcard = true)
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

    val allRoles = roleInfo.availableRoleBindings

    logger.info(s"Going to process ${allRoles.size -> "roles"}")

    val index = appConfig.roles.map(c => (c.roleConfig.role, c)).toMap
    assert(roleInfo.availableRoleNames == index.keySet)

    allRoles.foreach {
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
          subLogger.info(s"About to output configs...")
          val mergedRoleConfig = configMerger.mergeFilter(appConfig.shared, List(loaded), _ => true, "configwriter")
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
    val excludedActivations = Set.empty[NESet[AxisPoint]] // TODO: val chosenActivations = parseActivations(cfg.excludeActivations)
    val bindings = roleAppPlanner.bootloader.input.bindings
    val verifier = PlanVerifier()
    val v = verifier.traceReachables[F](bindings, Roots(NESet(role.binding.key)), _ => true, excludedActivations)
    val reachable = v match {
      case Left(issues) =>
        throw new IllegalStateException(s"Cannot produce minimized config, dependency tracing failed: ${issues.niceList()}")

      case Right(value) => value
    }

    val filteredModule = bindings.filter(reachable.contains)

    val resolvedConfig = {
      getConfig(filteredModule.bindings).toSet + _HackyMandatorySection
    }
    val out = ConfigWriter.minimized(resolvedConfig, roleConfig)
    Some(out)
  }

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

  private def getConfig(bindings: Set[Binding]): Seq[ConfigPath] = {
    val configTags = bindings.toSeq.flatMap(_.tags).collect {
      case t: ConfTag =>
        t
    }
    configTags.flatMap(t => unpack(Seq(t.confPath), t.fieldsMeta))
  }

  private def unpack(path: Seq[String], meta0: ConfigMeta): Seq[ConfigPath] = {
    meta0 match {
      case ConfigMeta.ConfigMetaCaseClass(fields) =>
        fields.flatMap {
          case (name, meta) =>
            unpack(path :+ name, meta)
        }
      case ConfigMeta.ConfigMetaSealedTrait(branches) =>
        branches.toSeq.flatMap {
          case (name, meta) =>
            unpack(path :+ name, meta)
        }
      case ConfigMeta.ConfigMetaEmpty() => Seq(ConfigPath(path.mkString(".")))
      case ConfigMeta.ConfigMetaUnknown() => Seq(ConfigPath(path.mkString("."), wildcard = true))
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
    import scala.jdk.CollectionConverters.*

    val paths = requiredPaths.map(_.toPath)
    val wildcards = requiredPaths.collect { case c if c.wildcard => c.toPath }

    def filter(path: Seq[String], config: ConfigObject): ConfigObject = {
      config.entrySet().asScala.foldLeft(config) {
        case (c, e) =>
          val key = e.getKey
          val npath = path :+ key
          val pathKey = npath.mkString(".")
          if (paths.contains(pathKey) || paths.exists(_.startsWith(pathKey + ".")) || wildcards.exists(pathKey.startsWith)) {
            e.getValue match {
              case configObject: ConfigObject => c.withValue(key, filter(npath, configObject))
              case _ => c
            }
          } else {
            c.withoutKey(key)
          }
      }
    }

    val filtered = filter(Seq.empty, source.root()).toConfig
    filtered
  }

  final case class ConfigPath(parts: Seq[String], wildcard: Boolean) {
    def toPath: String = parts.mkString(".")
  }
  object ConfigPath {
    def apply(path: String, wildcard: Boolean = false): ConfigPath = new ConfigPath(ArraySeq.unsafeWrapArray(path.split('.')), wildcard)
  }

}
