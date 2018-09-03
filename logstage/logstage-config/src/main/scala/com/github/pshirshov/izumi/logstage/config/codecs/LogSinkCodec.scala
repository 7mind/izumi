package com.github.pshirshov.izumi.logstage.config.codecs

import com.github.pshirshov.izumi.fundamentals.typesafe.config.ConfigReader
import com.github.pshirshov.izumi.logstage.api.logger.LogSink
import com.github.pshirshov.izumi.logstage.api.rendering.RenderingPolicy
import com.github.pshirshov.izumi.logstage.config.codecs.LogSinkCodec.{LogSinkMapper, NamedLogSink, _}
import com.typesafe.config.{Config, ConfigFactory, ConfigObject, ConfigValue}

import scala.util.Try

class LogSinkCodec(policyReader: RenderingPolicyCodec, mappers: Set[LogSinkMapper[LogSink]]) extends ConfigReader[LogSink] {
  private val instancesMappers = scala.collection.mutable.HashMap.empty[String, NamedLogSink]
  private val mappersMem = scala.collection.mutable.HashMap.empty[String, (Config, RenderingPolicy) => _ <: LogSink]

  mappers.map { m => mappersMem.put(m.path.toString, m.instantiate) }

  def fetchLogSink(id: String): Option[LogSink] = {
    instancesMappers.get(id).map(_.sink)
  }

  override def apply(configValue: ConfigValue): Try[LogSink] = {
    val config = configValue.asInstanceOf[ConfigObject].toConfig
    val logsinkIdMaybe = Try(config.getString(configKeyId)).toOption
    val result = logsinkIdMaybe match {
      case Some(id) =>
        Try(instancesMappers.getOrElseUpdate(id, {
          val logSinkParams = id match {
            case sinkId if sinkId == configKeyDefaultIdentity =>
              parseAsDefault(config)
            case _ =>
              parseAsNamed(config)
          }
          instantiatePathAndCfg(id, logSinkParams)
        }))
      case None =>
        retrieveDefault
    }
    result.map(_.sink)
  }

  private def parseAsDefault(config: Config): LogSinkUnaplied = {
    parse(config, configKeyPathFallback, _.getOrElse(throw new IllegalArgumentException("missed params property for default rendering policy")))
  }

  private def parseAsNamed(config: Config): LogSinkUnaplied = {
    parse(config, configKeyPathFallback, {
      paramsCfg =>
        val curParams = paramsCfg.getOrElse(ConfigFactory.empty())
        val defaultSink = instancesMappers(configKeyDefaultIdentity)
        val cfg = curParams.withFallback(defaultSink.config).resolve()
        cfg
    })
  }

  private def parse(config: Config,
                    fallBackOnPath: Try[String] => String,
                    fallBackOnParams: Try[Config] => Config): LogSinkUnaplied = {
    val path = fallBackOnPath(Try(config.getString(configKeyPath)))
    val params = fallBackOnParams(Try(config.getConfig(configKeyConstructorParams)))

    val policyId = Try(config.getString(configKeyPolicyId)).toOption
      .getOrElse(throw new IllegalArgumentException(s"Missed $configKeyPolicyId key for LogSink"))

    LogSinkUnaplied(path, params, policyId)
  }

  private val configKeyPathFallback: Try[String] => String = _.getOrElse(throw new IllegalArgumentException("LogSink full name should be defined"))


  private def instantiatePathAndCfg(id: String, logSinkUnaplied: LogSinkUnaplied): NamedLogSink = {
    val mapper = mappersMem.getOrElse(logSinkUnaplied.fullName, throw new IllegalArgumentException(s"LogSink instance mapper for ${logSinkUnaplied.fullName} not found. Maybe you forgot to add?"))
    val renderingPolicy = policyReader.fetchRenderingPolicy(logSinkUnaplied.renderingPolicyId).getOrElse(throw new IllegalArgumentException(s"Can not find requested instance of RenderingPolicy ${logSinkUnaplied.renderingPolicyId}. It seems that it was not defiend in `renderingPolicies` config section"))
    instancesMappers.getOrElseUpdate(id, NamedLogSink(mapper(logSinkUnaplied.referenceConfig, renderingPolicy), logSinkUnaplied.referenceConfig))
  }

  private def retrieveDefault: Try[NamedLogSink] = {
    Try {
      instancesMappers
        .getOrElse(configKeyDefaultIdentity,
          throw new IllegalArgumentException("default LogSink instance not found"))
    }
  }
}

object LogSinkCodec {

  import scala.reflect.runtime.universe

  abstract class LogSinkMapper[+T <: LogSink : universe.TypeTag] {
    def path: universe.Type = universe.typeOf[T]
    def instantiate(config: Config, renderingPolicy: RenderingPolicy): T
  }

  case class NamedLogSink(id: Symbol, sink: LogSink, config: Config)

  object NamedLogSink {
    def apply(logSink: LogSink, config: Config): NamedLogSink = {
      val hash = logSink.hashCode().toString
      new NamedLogSink(Symbol.apply(hash), logSink, config)
    }
  }

  final val configKeyDefaultIdentity = "default"
  final val configKeyId = "id"
  final val configKeyPath = "path"
  final val configKeyPolicyId = "policy"
  final val configKeyConstructorParams = "params"

  case class LogSinkUnaplied(fullName: String, referenceConfig: Config, renderingPolicyId: String)
}
