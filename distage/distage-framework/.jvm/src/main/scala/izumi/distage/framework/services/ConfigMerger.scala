package izumi.distage.framework.services

import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions}
import izumi.distage.config.model.*
import izumi.distage.model.definition.Id
import izumi.fundamentals.platform.strings.IzString.*
import izumi.logstage.api.IzLogger

import scala.jdk.CollectionConverters.*
import scala.util.Try

trait ConfigMerger {
  def merge(shared: List[ConfigLoadResult.Success], role: List[LoadedRoleConfigs]): Config
}

object ConfigMerger {
  class ConfigMergerImpl(logger: IzLogger @Id("early")) extends ConfigMerger {
    override def merge(shared: List[ConfigLoadResult.Success], role: List[LoadedRoleConfigs]): Config = {
//      val (roleReferenceConfigs, roleExplicitConfigs) = (configArgs.role: Iterable[(String, Option[File])]).partitionMap {
//        case (role, None) => Left(configLocation.forRole(role))
//        case (_, Some(file)) => Right(ConfigSource.File(file))
//      }
//
//      val allConfigs = (roleExplicitConfigs.iterator ++ commonExplicitConfigs ++ roleReferenceConfigs.iterator.flatten ++ commonReferenceConfigs).toList
//
//      val (cfgInfo, loaded) = loadConfigSources(allConfigs)
//
//      logger.info(s"Using system properties with fallback ${cfgInfo.niceList() -> "config files"}")
//
//      val (good, bad) = loaded.partition(_._2.isSuccess)
//
//      if (bad.nonEmpty) {
//        val failuresList = bad.collect {
//          case (s, Failure(f)) =>
//            s"$s: $f"
//        }
//        val failures = failuresList.niceList()
//
//        logger.error(s"Failed to load configs: $failures")
//        throw new ConfigLoaderException(s"Failed to load configs: failures=$failures", failuresList)
//      } else {
//
//      }

      val cfgInfo = (shared ++ role.flatMap(_.loaded)).map(c => c.clue)
      logger.info(s"Using system properties with fallback ${cfgInfo.niceList() -> "config files"}")

      val toMerge = shared ++ role.filter(_.roleConfig.active).flatMap(_.loaded)

      val folded = foldConfigs(toMerge)

      ConfigFactory
        .systemProperties()
        .withFallback(folded)
        .resolve()
    }

    private def foldConfigs(roleConfigs: Iterable[ConfigLoadResult.Success]): Config = {
      roleConfigs.iterator.foldLeft(ConfigFactory.empty()) {
        case (acc, loaded) =>
          verifyConfigs(loaded, acc)
          acc.withFallback(loaded.config)
      }
    }

    protected def verifyConfigs(loaded: ConfigLoadResult.Success, acc: Config): Unit = {
      val duplicateKeys = getKeys(acc) intersect getKeys(loaded.config)
      if (duplicateKeys.nonEmpty) {
        loaded.src match {
          case ConfigSource.Resource(_, ResourceConfigKind.Development) =>
            logger.debug(s"Some keys in supplied ${loaded.src -> "development config"} duplicate already defined keys: ${duplicateKeys.niceList() -> "keys" -> null}")
          case _ =>
            logger.warn(s"Some keys in supplied ${loaded.src -> "config"} duplicate already defined keys: ${duplicateKeys.niceList() -> "keys" -> null}")
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
}
