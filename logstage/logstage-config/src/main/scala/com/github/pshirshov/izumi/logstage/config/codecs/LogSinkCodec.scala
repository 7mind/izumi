package com.github.pshirshov.izumi.logstage.config.codecs

import com.github.pshirshov.izumi.fundamentals.reflection.SafeType0
import com.github.pshirshov.izumi.fundamentals.typesafe.config.{ConfigReader, RuntimeConfigReader, RuntimeConfigReaderCodecs, RuntimeConfigReaderDefaultImpl}
import com.github.pshirshov.izumi.logstage.api.logger.LogSink
import com.github.pshirshov.izumi.logstage.api.rendering.RenderingPolicy
import com.github.pshirshov.izumi.logstage.config.codecs.LogSinkCodec.{LogSinkMapper, _}
import com.typesafe.config.{Config, ConfigFactory, ConfigObject, ConfigValue}

import scala.reflect.runtime.universe
import scala.util.Try

class LogSinkCodec(policyMappers: Set[LogSinkMapper[LogSink, _]]) extends ConfigReader[LogSink] {
  private val mappersMem : Map[String, Config => Try[LogSink]]= {
    Map(policyMappers.map(m => (m.path.toString, m.instantiate _)).toSeq :_*)
  }

  override def apply(configValue: ConfigValue): Try[LogSink] = {
    val config = configValue.asInstanceOf[ConfigObject].toConfig
    val path = Try(config.getString(renderingPath)).getOrElse(throw new IllegalArgumentException("from config to instance mapper not found. Maybe you forgot to add?"))
    val params = Try(config.getConfig(renderingParams)).getOrElse(ConfigFactory.empty())
    val mapper = mappersMem.getOrElse(path, throw new IllegalArgumentException("from config to instance mapper not found. Maybe you forgot to add?"))
    mapper(params)
  }

}

object LogSinkCodec {

  abstract class ClassMapper[+T: universe.TypeTag, C : universe.TypeTag] {

    def extraCodecs : Map[SafeType0[universe.type], ConfigReader[_]] = Map.empty

    def path: universe.Type = universe.typeOf[T]

    def reader: RuntimeConfigReader = new RuntimeConfigReaderDefaultImpl(RuntimeConfigReaderCodecs.default.readerCodecs ++ extraCodecs)

    def apply(props: C) : T

    def instantiate(config : Config) : Try[T] = {
      withConfig(config).map(apply)
    }

    protected def withConfig(config: Config): Try[C] = {
      Try(reader.readConfigAsCaseClass(config, SafeType0.get[C])).flatMap(any => Try(any.asInstanceOf[C]))
    }
  }

  abstract class LogSinkMapper[+T <: LogSink : universe.TypeTag, C : universe.TypeTag]
    extends ClassMapper[T, C] {
    override def instantiate(config: Config): Try[T] = super.instantiate(config)
  }

  case class NamedRenderingPolicy(id: Symbol, policy: RenderingPolicy, config: Config)


  private final val renderingPath = "path"
  private final val renderingParams = "params"
}
