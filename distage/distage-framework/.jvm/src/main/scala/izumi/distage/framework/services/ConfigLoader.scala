package izumi.distage.framework.services

import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions}
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.services.ConfigLoader.LocalFSImpl.{ConfigLoaderException, ConfigSource, ResourceConfigKind}
import izumi.distage.model.definition.Id
import izumi.distage.model.exceptions.DIException
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.model.meta.RolesInfo
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.resources.IzResources
import izumi.fundamentals.platform.resources.IzResources.{LoadablePathReference, UnloadablePathReference}
import izumi.fundamentals.platform.strings.IzString.*
import izumi.logstage.api.IzLogger

import java.io.{File, FileNotFoundException}
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}

/**
  * Default config resources:
  *   - `\${roleName}.conf`
  *   - `\${roleName}-reference.conf`
  *   - `\${roleName}-reference-dev.conf`
  *   - `application.conf`
  *   - `application-reference.conf`
  *   - `application-reference-dev.conf`
  *   - `common.conf`
  *   - `common-reference.conf`
  *   - `common-reference-dev.conf`
  *
  * NOTE: You can change default config locations by overriding `make[ConfigLocation]`
  * binding in [[izumi.distage.roles.RoleAppMain#roleAppBootOverrides]] (defaults defined in [[izumi.distage.roles.RoleAppBootModule]])
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
  *
  * @see [[ConfigLoader.ConfigLocation]]
  * @see [[ConfigLoader.LocalFSImpl]]
  */
trait ConfigLoader extends AbstractConfigLoader {}

object ConfigLoader {
  def empty: ConfigLoader = () => AppConfig(ConfigFactory.empty())

  import scala.collection.compat.*

  trait ConfigLocation {
    def forRole(roleName: String): Seq[ConfigSource] = ConfigLocation.defaultConfigReferences(roleName)
    def forBase(filename: String): Seq[ConfigSource] = ConfigLocation.defaultConfigReferences(filename)
    def defaultBaseConfigs: Seq[String] = ConfigLocation.defaultBaseConfigs
  }
  object ConfigLocation {
    object Default extends ConfigLocation

    def defaultBaseConfigs: Seq[String] = Seq("application", "common")

    def defaultConfigReferences(name: String): Seq[ConfigSource] = {
      Seq(
        ConfigSource.Resource(s"$name.conf", ResourceConfigKind.Primary),
        ConfigSource.Resource(s"$name-reference.conf", ResourceConfigKind.Primary),
        ConfigSource.Resource(s"$name-reference-dev.conf", ResourceConfigKind.Development),
      )
    }
  }

  final case class Args(
    global: Option[File],
    role: Map[String, Option[File]],
  )
  object Args {
    def makeConfigLoaderArgs(
      parameters: RawAppArgs,
      rolesInfo: RolesInfo,
    ): ConfigLoader.Args = {
      val maybeGlobalConfig = parameters.globalParameters.findValue(RoleAppMain.Options.configParam).asFile
      val emptyRoleConfigs = rolesInfo.availableRoleNames.map(_ -> None).toMap
      val specifiedRoleConfigs = parameters.roles.iterator
        .map(roleParams => roleParams.role -> roleParams.roleParameters.findValue(RoleAppMain.Options.configParam).asFile)
        .toMap
      ConfigLoader.Args(maybeGlobalConfig, (emptyRoleConfigs ++ specifiedRoleConfigs).view.toMap)
    }

    def empty: ConfigLoader.Args = ConfigLoader.Args(None, Map.empty)
  }

  open class LocalFSImpl(
    logger: IzLogger @Id("early"),
    configLocation: ConfigLocation,
    configArgs: ConfigLoader.Args,
  ) extends ConfigLoader {
    protected def resourceClassLoader: ClassLoader = getClass.getClassLoader

    def loadConfig(): AppConfig = {
      val commonReferenceConfigs = configLocation.defaultBaseConfigs.flatMap(configLocation.forBase)
      val commonExplicitConfigs = configArgs.global.map(ConfigSource.File.apply).toList

      val (roleReferenceConfigs, roleExplicitConfigs) = (configArgs.role: Iterable[(String, Option[File])]).partitionMap {
        case (role, None) => Left(configLocation.forRole(role))
        case (_, Some(file)) => Right(ConfigSource.File(file))
      }

      val allConfigs = (roleExplicitConfigs.iterator ++ commonExplicitConfigs ++ roleReferenceConfigs.iterator.flatten ++ commonReferenceConfigs).toList

      val (cfgInfo, loaded) = loadConfigSources(allConfigs)

      logger.info(s"Using system properties with fallback ${cfgInfo.niceList() -> "config files"}")

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

    protected def loadConfigSources(allConfigs: List[ConfigSource]): (List[String], List[(ConfigSource, Try[Config])]) = {
      allConfigs.map(loadConfigSource).unzip
    }

    protected def loadConfigSource(configSource: ConfigSource): (String, (ConfigSource, Try[Config])) = configSource match {
      case r: ConfigSource.Resource =>
        def tryLoadResource(): Try[Config] = {
          Try(ConfigFactory.parseResources(resourceClassLoader, r.name)).flatMap {
            cfg =>
              if (cfg.origin().resource() eq null) {
                Failure(new FileNotFoundException(s"Couldn't find config file $r"))
              } else Success(cfg)
          }
        }

        IzResources(resourceClassLoader).getPath(r.name) match {
          case Some(LoadablePathReference(path, _)) =>
            s"$r (available: $path)" -> (r -> tryLoadResource())
          case Some(UnloadablePathReference(path)) =>
            s"$r (exists: $path)" -> (r -> tryLoadResource())
          case None =>
            s"$r (missing)" -> (r -> Success(ConfigFactory.empty()))
        }

      case f: ConfigSource.File =>
        if (f.file.exists()) {
          s"$f (exists: ${f.file.getCanonicalPath})" -> (f -> Try(ConfigFactory.parseFile(f.file)).flatMap {
            cfg => if (cfg.origin().filename() ne null) Success(cfg) else Failure(new FileNotFoundException(s"Couldn't find config file $f"))
          })
        } else {
          s"$f (missing)" -> (f -> Success(ConfigFactory.empty()))
        }
    }

    protected def foldConfigs(roleConfigs: IterableOnce[(ConfigSource, Config)]): Config = {
      roleConfigs.iterator.foldLeft(ConfigFactory.empty()) {
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
