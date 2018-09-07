package com.github.pshirshov.izumi.logstage.distage

import com.github.pshirshov.izumi.distage.model.definition.BootstrapModuleDef
import com.github.pshirshov.izumi.fundamentals.reflection.SafeType0
import com.github.pshirshov.izumi.fundamentals.typesafe.config.{ConfigReader, RuntimeConfigReader}
import com.github.pshirshov.izumi.logstage.api.logger.LogSink
import com.github.pshirshov.izumi.logstage.api.rendering.RenderingPolicy
import com.github.pshirshov.izumi.logstage.config.codecs.LogSinkCodec.LogSinkMapper
import com.github.pshirshov.izumi.logstage.config.codecs.RenderingPolicyCodec
import com.github.pshirshov.izumi.logstage.config.codecs.RenderingPolicyCodec.RenderingPolicyMapper

import scala.reflect.runtime.universe

abstract class LogSinkMapperModule[T <: LogSink : universe.TypeTag, C: universe.TypeTag] extends BootstrapModuleDef {

  def init(constructor: C): T

  many[LogSinkMapper[_ <: LogSink, _]].add {

    policyMappers: Set[RenderingPolicyMapper[_ <: RenderingPolicy, _]] =>

        val policyCodec = new RenderingPolicyCodec(policyMappers)

        new LogSinkMapper[T, C] {

          override def apply(props: C): T = init(props)

          override def extraCodecs: Map[SafeType0[universe.type], ConfigReader[_]] = super.extraCodecs ++ Map(
            SafeType0.get[RenderingPolicy] -> policyCodec
          )
        }
  }
}
