package com.github.pshirshov.izumi.logstage.config.codecs

import com.github.pshirshov.izumi.fundamentals.typesafe.config.ConfigReader
import com.github.pshirshov.izumi.logstage.api.logger.LogSink
import com.github.pshirshov.izumi.logstage.config.codecs.LogSinkCodec.LogSinkMapper
import com.typesafe.config.{Config, ConfigFactory, ConfigObject, ConfigValue}

import scala.reflect.runtime.universe
import scala.util.Try

class LogSinkCodec(policyMappers: Set[LogSinkMapper[LogSink]]) extends ConfigReader[LogSink] {
  private val mappersMem : Map[String, Config => Try[LogSink]] = {
    policyMappers.map(m => (m.path, m.instantiate _)).toMap
  }

  override def apply(configValue: ConfigValue): Try[LogSink] = Try {
    val config = configValue.asInstanceOf[ConfigObject].toConfig
    val path = Try(config.getString(renderingPath)).getOrElse(throw new IllegalArgumentException("from config to instance mapper not found. Maybe you forgot to add?"))
    val params = Try(config.getConfig(renderingParams)).getOrElse(ConfigFactory.empty())
    val mapper = mappersMem.getOrElse(path, throw new IllegalArgumentException("from config to instance mapper not found. Maybe you forgot to add?"))
    mapper(params)
  }.flatten

  private[this] final val renderingPath = "path"
  private[this] final val renderingParams = "params"
}

object LogSinkCodec {

  abstract class LogSinkMapper[+T <: LogSink : universe.TypeTag] extends ClassMapper[T]
  abstract class LogSinkMapperImpl[+T <: LogSink : universe.TypeTag, C](implicit cTag: universe.TypeTag[C]) extends LogSinkMapper[T] {
    override type Params = C

    override implicit protected final val paramsTag: universe.TypeTag[Params] = cTag
  }

}
