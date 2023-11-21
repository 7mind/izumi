package izumi.distage.roles.bundled

import com.typesafe.config.{Config, ConfigFactory}
import distage.config.AppConfig
import izumi.distage.config.model.ConfTag
import izumi.distage.framework.services.RoleAppPlanner
import izumi.distage.model.definition.Id
import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.roles.bundled.ConfigWriter.WriteReference
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams
import izumi.fundamentals.platform.cli.model.schema.{ParserDef, RoleParserSchema}
import izumi.fundamentals.platform.resources.ArtifactVersion
import izumi.logstage.api.IzLogger

import java.nio.file.Paths
import scala.annotation.{nowarn, unused}
import scala.collection.compat.immutable.ArraySeq

final class ConfigWriter[F[_]](
  logger: IzLogger,
  @unused launcherVersion: ArtifactVersion @Id("launcher-version"),
  roleInfo: RolesInfo,
  @unused roleAppPlanner: RoleAppPlanner,
  @unused appConfig: AppConfig,
  F: QuasiIO[F],
) extends RoleTask[F]
  with BundledTask {

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
    ???
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
