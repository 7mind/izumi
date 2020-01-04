package izumi.distage.framework.services

import java.io.File

import com.typesafe.config.{Config, ConfigFactory}
import distage.config.AppConfig
import izumi.fundamentals.platform.resources.IzResources
import izumi.fundamentals.platform.strings.IzString._
import izumi.logstage.api.IzLogger

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

trait ConfigLoader {
  def buildConfig(): AppConfig

  final def map(f: AppConfig => AppConfig): ConfigLoader = {
    () => f(buildConfig())
  }
}

object ConfigLoader {

  /**
   * default config locations:
   *   - common.conf
   *   - common-reference.conf
   *   - common-reference-dev.conf
   *   - $roleName.conf
   *   - $roleName-reference.conf
   *   - $roleName-reference-dev.conf
   */
  class LocalFSImpl(
    logger: IzLogger,
    primaryConfig: Option[File],
    roleConfigs: Map[String, Option[File]],
  ) extends ConfigLoader {

    import LocalFSImpl._

    def buildConfig(): AppConfig = {
      val commonConfigFile = toConfig("common", primaryConfig)

      val roleConfigFiles = roleConfigs.flatMap {
        case (roleName, roleConfig) =>
          toConfig(roleName, roleConfig)
      }
        .toList

      val allConfigs = roleConfigFiles ++ commonConfigFile

      val cfgInfo = allConfigs.map {
        case r: ConfigSource.Resource =>
          IzResources.getPath(r.name) match {
            case Some(value) =>
              s"$r (exists: ${value.path})"
            case None =>
              s"$r (missing)"
          }

        case f: ConfigSource.File =>
          if (f.file.exists()) {
            s"$f (exists: ${f.file.getCanonicalPath})"
          } else {
            s"$f (missing)"
          }
      }

      logger.info(s"Using system properties with fallback ${cfgInfo.niceList() -> "config files"}")

      val loaded = allConfigs.map {
        case s@ConfigSource.File(file) =>
          s -> Try(ConfigFactory.parseFile(file))

        case s@ConfigSource.Resource(name, _) =>
          s -> Try(ConfigFactory.parseResources(name))
      }

      val (good, bad) = loaded.partition(_._2.isSuccess)

      if (bad.nonEmpty) {
        val failures = bad.collect {
          case (s, Failure(f)) =>
            s"$s: $f"
        }

        logger.error(s"Failed to load configs: ${failures.niceList() -> "failures"}")
      }

      val folded = foldConfigs(good.collect {
        case (src, Success(c)) => src -> c
      })

      val config = ConfigFactory.systemProperties()
        .withFallback(folded)
        .resolve()

      AppConfig(config)
    }

    protected def defaultConfigReferences(name: String): Seq[ConfigSource] = {
      Seq(
        ConfigSource.Resource(s"$name.conf", ResourceConfigKind.Primary),
        ConfigSource.Resource(s"$name-reference.conf", ResourceConfigKind.Primary),
        ConfigSource.Resource(s"$name-reference-dev.conf", ResourceConfigKind.Development),
      )
    }

    protected def foldConfigs(roleConfigs: Seq[(ConfigSource, Config)]): Config = {
      roleConfigs.foldLeft(ConfigFactory.empty()) {
        case (acc, (src, cfg)) =>
          verifyConfigs(src, cfg, acc)
          acc.withFallback(cfg)
      }
    }

    protected def verifyConfigs(src: ConfigSource, cfg: Config, acc: Config): Unit = {
      val duplicateKeys = acc.entrySet().asScala.map(_.getKey).intersect(cfg.entrySet().asScala.map(_.getKey))
      if (duplicateKeys.nonEmpty) {
        src match {
          case ConfigSource.Resource(_, ResourceConfigKind.Development) =>
            logger.debug(s"Some keys in supplied ${src -> "development config"} duplicate already defined keys: ${duplicateKeys.niceList() -> "keys" -> null}")

          case _ =>
            logger.warn(s"Some keys in supplied ${src -> "config"} duplicate already defined keys: ${duplicateKeys.niceList() -> "keys" -> null}")
        }
      }
    }

    private def toConfig(name: String, maybeConfigFile: Option[File]): Seq[ConfigSource] = {
      maybeConfigFile.fold(defaultConfigReferences(name)) {
        f =>
          Seq(ConfigSource.File(f))
      }
    }
  }

  object LocalFSImpl {
    sealed trait ResourceConfigKind
    object ResourceConfigKind {
      case object Primary extends ResourceConfigKind
      case object Development extends ResourceConfigKind
    }

    sealed trait ConfigSource
    object ConfigSource {
      final case class Resource(name: String, kind: ResourceConfigKind) extends ConfigSource {
        override def toString: String = s"resource:$name"
      }
      final case class File(file: java.io.File) extends ConfigSource {
        override def toString: String = s"file:$file"
      }
    }
  }

}
