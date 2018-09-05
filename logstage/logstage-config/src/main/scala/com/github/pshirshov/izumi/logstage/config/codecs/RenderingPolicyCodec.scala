package com.github.pshirshov.izumi.logstage.config.codecs

import com.github.pshirshov.izumi.fundamentals.reflection.SafeType0
import com.github.pshirshov.izumi.fundamentals.typesafe.config.{ConfigReader, RuntimeConfigReader, RuntimeConfigReaderCodecs, RuntimeConfigReaderDefaultImpl}
import com.github.pshirshov.izumi.logstage.api.rendering.{RenderingOptions, RenderingPolicy}
import com.github.pshirshov.izumi.logstage.config.codecs.RenderingPolicyCodec.{RenderingPolicyMapper, _}
import com.typesafe.config.{Config, ConfigFactory, ConfigObject, ConfigValue}

import scala.reflect.runtime.universe
import scala.util.Try

class RenderingPolicyCodec(policyMappers: Set[RenderingPolicyMapper[_ <: RenderingPolicy, _]]) extends ConfigReader[RenderingPolicy] {
  private val mappersMem : Map[String, Config => Try[_ <: RenderingPolicy]]= {
     Map(policyMappers.map(m => (m.path.toString, m.instantiate _)).toSeq :_*)
  }

  override def apply(configValue: ConfigValue): Try[RenderingPolicy] = {
    val config = configValue.asInstanceOf[ConfigObject].toConfig
    val path = Try(config.getString(renderingPath)).getOrElse(throw new IllegalArgumentException("from config to instance mapper not found. Maybe you forgot to add?"))
    val params = Try(config.getConfig(renderingParams)).getOrElse(ConfigFactory.empty())
    val mapper = mappersMem.getOrElse(path, throw new IllegalArgumentException("from config to instance mapper not found. Maybe you forgot to add?"))
    mapper(params)
  }

}

object RenderingPolicyCodec {

  abstract class RenderingPolicyMapper[+T <: RenderingPolicy : universe.TypeTag, C : universe.TypeTag] {
    def path: universe.Type = universe.typeOf[T]

    def reader: RuntimeConfigReader = new RuntimeConfigReaderDefaultImpl(RuntimeConfigReaderCodecs.default.readerCodecs)

    def apply(props: C) : T
    def instantiate(config : Config) : Try[T] = {
      withConfig(config).map(apply)
    }

    def withConfig(config: Config): Try[C] = {
      Try(reader.readConfigAsCaseClass(config, SafeType0.get[C])).flatMap(any => Try(any.asInstanceOf[C]))
    }
  }

  case class NamedRenderingPolicy(id: Symbol, policy: RenderingPolicy, config: Config)

  object NamedRenderingPolicy {
    def apply(policy: RenderingPolicy, config: Config): NamedRenderingPolicy = {
      val hash = policy.hashCode().toString
      new NamedRenderingPolicy(Symbol.apply(hash), policy, config)
    }
  }

  private final val renderingPath = "path"
  private final val renderingParams = "params"
}
