package com.github.pshirshov.izumi.distage.roles.services

import java.io.File

import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzResources
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.typesafe.config.{Config, ConfigFactory}

import scala.util.{Failure, Success, Try}

class ConfigLoaderLocalFilesystemImpl(logger: IzLogger, primaryConfig: Option[File], roles: Map[String, Option[File]]) extends ConfigLoader {
  import ConfigLoaderLocalFilesystemImpl._
  import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

  import scala.collection.JavaConverters._

  def buildConfig(): AppConfig = {
    val commonConfigFile = primaryConfig
      .fold(ConfigSource.Resource("common-reference.conf"): ConfigSource)(f => ConfigSource.File(f))

    val roleConfigFiles = roles.map {
      case (roleName, roleConfig) =>
        roleConfig.fold(ConfigSource.Resource(s"$roleName-reference.conf"): ConfigSource)(f => ConfigSource.File(f))
    }
      .toList

    val allConfigs = roleConfigFiles :+ commonConfigFile

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

    logger.info(s"Using ${cfgInfo.niceList() -> "config files"}")
    logger.info(s"Using system properties")

    val loaded = allConfigs.map {
      case s@ConfigSource.File(file) =>
        s -> Try(ConfigFactory.parseFile(file))

      case s@ConfigSource.Resource(name) =>
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

    val folded = foldConfigs(good.collect({ case (_, Success(c)) => c }))

    val config = ConfigFactory.systemProperties()
      .withFallback(folded)
      .resolve()

    AppConfig(config)
  }

  protected def foldConfigs(roleConfigs: Seq[Config]): Config = {
    roleConfigs.foldLeft(ConfigFactory.empty()) {
      case (acc, cfg) =>
        verifyConfigs(cfg, acc)
        acc.withFallback(cfg)
    }
  }

  protected def verifyConfigs(cfg: Config, acc: Config): Unit = {
    val duplicateKeys = acc.entrySet().asScala.map(_.getKey).intersect(cfg.entrySet().asScala.map(_.getKey))
    if (duplicateKeys.nonEmpty) {
      logger.warn(s"Found duplicated keys in supplied configs: ${duplicateKeys.niceList() -> "keys" -> null}")
    }
  }

}

object ConfigLoaderLocalFilesystemImpl {
  sealed trait ConfigSource

  object ConfigSource {

    final case class Resource(name: String) extends ConfigSource {
      override def toString: String = s"resource:$name"
    }

    final case class File(file: java.io.File) extends ConfigSource {
      override def toString: String = s"file:$file"
    }

  }
}
