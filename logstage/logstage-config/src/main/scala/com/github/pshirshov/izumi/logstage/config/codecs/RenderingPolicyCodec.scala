package com.github.pshirshov.izumi.logstage.config.codecs

import com.github.pshirshov.izumi.distage.config.codec.ConfigReader
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.u
import com.github.pshirshov.izumi.logstage.api.rendering.RenderingPolicy
import com.github.pshirshov.izumi.logstage.api.rendering.RenderingPolicy.PolicyConfig
import com.github.pshirshov.izumi.logstage.config.codecs.RenderingPolicyCodec.{NamedRenderingPolicy, RenderingPolicyMapper, _}
import com.typesafe.config.{Config, ConfigFactory, ConfigObject, ConfigValue}

import scala.util.Try

class RenderingPolicyCodec(policyMappers: Set[RenderingPolicyMapper[_ <: RenderingPolicy]], policyConfig: ConfigReader[PolicyConfig]) extends ConfigReader[RenderingPolicy] {
  private val policyInstancesMappers = scala.collection.mutable.HashMap.empty[String, NamedRenderingPolicy]
  private val policyMappersMem = scala.collection.mutable.HashMap.empty[String, PolicyConfig => _ <: RenderingPolicy]

  policyMappers.map { m => policyMappersMem.put(m.path.toString, m.instantiate) }

  def fetchRenderingPolicy(id: String): Option[RenderingPolicy] = {
    policyInstancesMappers.get(id).map(_.policy)
  }

  override def apply(configValue: ConfigValue): Try[RenderingPolicy] = {
    val config = configValue.asInstanceOf[ConfigObject].toConfig
    val policyIdMaybe = Try(config.getString(policyIdentity)).toOption
    val result = policyIdMaybe match {
      case Some(policyId) =>
        Try(policyInstancesMappers.getOrElseUpdate(policyId, {
          val (path, cfg) = policyId match {
            case id if id == defaultPolicyId =>
              parseAsDefault(config)
            case other =>
              parseAsNamed(config, other)
          }
          instantiatePathAndCfg(cfg, path, policyId).get
        }))
      case None =>
        retrieveDefault
    }
    result.map(_.policy)
  }

  private def parseAsDefault(config: Config): (String, Config) = {
    parse(config, defaultPolicyId, renderingPolicyFallback, _.getOrElse(throw new IllegalArgumentException("missed params property for default rendering policy")))
  }

  private def parseAsNamed(config: Config, id: String): (String, Config) = {
    parse(config, id, renderingPolicyFallback, {
      paramsCfg =>
        val curParams = paramsCfg.getOrElse(ConfigFactory.empty())
        val defaultPolicy = policyInstancesMappers(defaultPolicyId)
        val cfg = curParams.withFallback(defaultPolicy.config).resolve()
        cfg
    })
  }

  private def parse(config: Config, id: String,
                    fallBackOnPath: Try[String] => String,
                    fallBackOnParams: Try[Config] => Config): (String, Config) = {
    val path = fallBackOnPath(Try(config.getString(renderingPath)))
    val params = fallBackOnParams(Try(config.getConfig(renderingParams)))
    (path, params)
  }

  private val renderingPolicyFallback: Try[String] => String = _.getOrElse(throw new IllegalArgumentException("Rendering policy full name should be defined"))


  private def instantiatePathAndCfg(config: Config, path: String, id: String): Try[NamedRenderingPolicy] = {
    val mapper = policyMappersMem.getOrElse(path, throw new IllegalArgumentException("from config to instance mapper not found. Maybe you forgot to add?"))
    println(config.root())
    policyConfig.apply(config.root()).map {
      cfg => policyInstancesMappers.getOrElseUpdate(id, NamedRenderingPolicy(mapper(cfg), config))
    }
  }

  private def retrieveDefault: Try[NamedRenderingPolicy] = {
    Try {
      policyInstancesMappers
        .getOrElse(defaultPolicyId,
          throw new IllegalArgumentException("default rendering policy was not found"))
    }
  }
}

object RenderingPolicyCodec {

  abstract class RenderingPolicyMapper[T <: RenderingPolicy : u.TypeTag] {
    def path: u.Type = u.typeOf[T]

    def instantiate(policyConfig: PolicyConfig): T
  }

  case class NamedRenderingPolicy(id: Symbol, policy: RenderingPolicy, config: Config)

  object NamedRenderingPolicy {
    def apply(policy: RenderingPolicy, config: Config): NamedRenderingPolicy = {
      val hash = policy.hashCode().toString
      new NamedRenderingPolicy(Symbol.apply(hash), policy, config)
    }
  }

  private final val defaultPolicyId = "default"
  private final val policyIdentity = "id"
  private final val renderingPath = "path"
  private final val renderingParams = "params"
}
