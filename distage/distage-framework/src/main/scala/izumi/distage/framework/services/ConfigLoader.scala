package izumi.distage.framework.services

import java.io.File

import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions}
import distage.config.AppConfig
import izumi.distage.framework.services.ConfigLoader.LocalFSImpl.{ConfigLoaderException, ConfigSource, ResourceConfigKind}
import izumi.distage.model.exceptions.DIException
import izumi.fundamentals.platform.resources.IzResources
import izumi.fundamentals.platform.strings.IzString._
import izumi.logstage.api.IzLogger

import scala.annotation.nowarn
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

/**
  * Default config resources:
  *   - $roleName.conf
  *   - $roleName-reference.conf
  *   - $roleName-reference-dev.conf
  *   - application.conf
  *   - application-reference.conf
  *   - application-reference-dev.conf
  *   - common.conf
  *   - common-reference.conf
  *   - common-reference-dev.conf
  *
  * When explicit configs are passed to the role launcher on the command-line using the `-c` option, they have higher priority than all the reference configs.
  * Role-specific configs on the command-line (`-c` option after `:role` argument) override global command-line configs (`-c` option given before the first `:role` argument).
  *
  * Example:
  *
  * {{{
  *   ./launcher -c global.conf :role1 -c role1.conf :role2 -c role2.conf
  * }}}
  *
  * Here configs will be loaded in the following order, with higher priority earlier:
  *
  *   - explicits: `role1.conf`, `role2.conf`, `global.conf`,
  *   - resources: `role1[-reference,-dev].conf`, `role2[-reference,-dev].conf`, ,`application[-reference,-dev].conf`, `common[-reference,-dev].conf`
  */
trait ConfigLoader {
  def loadConfig(): AppConfig

  final def map(f: AppConfig => AppConfig): ConfigLoader = () => f(loadConfig())
}

object ConfigLoader {
  val defaultBaseConfigs = Seq("application", "common")

  class LocalFSImpl(
    logger: IzLogger,
    baseConfig: Option[File],
    moreConfigs: Map[String, Option[File]],
    defaultBaseConfigs: Seq[String],
  ) extends ConfigLoader {

    @nowarn("msg=Unused import")
    def loadConfig(): AppConfig = {
      import scala.collection.compat._

      val commonReferenceConfigs = defaultBaseConfigs.flatMap(defaultConfigReferences)
      val commonExplicitConfigs = baseConfig.map(ConfigSource.File).toList

      val (roleReferenceConfigs, roleExplicitConfigs) = (moreConfigs: Iterable[(String, Option[File])]).partitionMap {
        case (role, None) => Left(defaultConfigReferences(role))
        case (_, Some(file)) => Right(ConfigSource.File(file))
      }

      val allConfigs = (roleExplicitConfigs.iterator ++ commonExplicitConfigs ++ roleReferenceConfigs.iterator.flatten ++ commonReferenceConfigs).toList

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
        case s @ ConfigSource.File(file) =>
          s -> Try(ConfigFactory.parseFile(file))

        case s @ ConfigSource.Resource(name, _) =>
          s -> Try(ConfigFactory.parseResources(name))
      }

      val (good, bad) = loaded.partition(_._2.isSuccess)

      if (bad.nonEmpty) {
        val failuresList = bad.collect {
          case (s, Failure(f)) =>
            s"$s: $f"
        }
        val failures = failuresList.niceList()

        logger.error(s"Failed to load configs: $failures")
        throw new ConfigLoaderException(s"Failed to load configs: failures=$failures", failuresList)
      } else {
        val folded = foldConfigs(good.collect {
          case (src, Success(c)) => src -> c
        })

        val config = ConfigFactory
          .systemProperties()
          .withFallback(folded)
          .resolve()

        AppConfig(config)
      }
    }

    protected def foldConfigs(roleConfigs: Seq[(ConfigSource, Config)]): Config = {
      roleConfigs.foldLeft(ConfigFactory.empty()) {
        case (acc, (src, cfg)) =>
          verifyConfigs(src, cfg, acc)
          acc.withFallback(cfg)
      }
    }

    protected def verifyConfigs(src: ConfigSource, cfg: Config, acc: Config): Unit = {
      val duplicateKeys = getKeys(acc) intersect getKeys(cfg)
      if (duplicateKeys.nonEmpty) {
        src match {
          case ConfigSource.Resource(_, ResourceConfigKind.Development) =>
            logger.debug(s"Some keys in supplied ${src -> "development config"} duplicate already defined keys: ${duplicateKeys.niceList() -> "keys" -> null}")
          case _ =>
            logger.warn(s"Some keys in supplied ${src -> "config"} duplicate already defined keys: ${duplicateKeys.niceList() -> "keys" -> null}")
        }
      }
    }

    protected def getKeys(c: Config): collection.Set[String] = {
      if (c.isResolved) {
        c.entrySet().asScala.map(_.getKey)
      } else {
        Try {
          c.resolve(ConfigResolveOptions.defaults().setAllowUnresolved(true))
        }.toOption.filter(_.isResolved) match {
          case Some(value) => value.entrySet().asScala.map(_.getKey)
          case None => Set.empty
        }
      }
    }

    protected def defaultConfigReferences(name: String): Seq[ConfigSource] = {
      Seq(
        ConfigSource.Resource(s"$name.conf", ResourceConfigKind.Primary),
        ConfigSource.Resource(s"$name-reference.conf", ResourceConfigKind.Primary),
        ConfigSource.Resource(s"$name-reference-dev.conf", ResourceConfigKind.Development),
      )
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

    final class ConfigLoaderException(message: String, val failures: List[String]) extends DIException(message)
  }

}
