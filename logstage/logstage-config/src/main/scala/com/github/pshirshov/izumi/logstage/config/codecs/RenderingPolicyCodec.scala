package com.github.pshirshov.izumi.logstage.config.codecs

import com.github.pshirshov.izumi.fundamentals.typesafe.config.ConfigReader
import com.github.pshirshov.izumi.logstage.api.rendering.RenderingPolicy
import com.github.pshirshov.izumi.logstage.config.codecs.RenderingPolicyCodec.RenderingPolicyMapper
import com.typesafe.config.{Config, ConfigFactory, ConfigObject, ConfigValue}

import scala.reflect.runtime.universe
import scala.util.Try

class RenderingPolicyCodec(policyMappers: Set[RenderingPolicyMapper[RenderingPolicy]]) extends ConfigReader[RenderingPolicy] {
  private val mappersMem : Map[String, Config => Try[RenderingPolicy]]= {
     policyMappers.map(m => (m.path.toString, m.instantiate _)).toMap
  }

  override def apply(configValue: ConfigValue): Try[RenderingPolicy] = Try {
    val config = configValue.asInstanceOf[ConfigObject].toConfig
    val path = Try(config.getString(renderingPath)).getOrElse(throw new IllegalArgumentException("from config to instance mapper not found. Maybe you forgot to add?"))
    val params = Try(config.getConfig(renderingParams)).getOrElse(ConfigFactory.empty())
    val mapper = mappersMem.getOrElse(path, throw new IllegalArgumentException("from config to instance mapper not found. Maybe you forgot to add?"))
    mapper(params)
  }.flatten

  private[this] final val renderingPath = "path"
  private[this] final val renderingParams = "params"
}

object RenderingPolicyCodec {

  abstract class RenderingPolicyMapper[+T <: RenderingPolicy : universe.TypeTag] extends ClassMapper[T]
  abstract class RenderingPolicyMapperImpl[+T <: RenderingPolicy : universe.TypeTag, C](implicit cTag: universe.TypeTag[C]) extends RenderingPolicyMapper[T] {
    override type Params = C

    override implicit protected final val paramsTag: universe.TypeTag[C] = cTag
  }

}
