package com.github.pshirshov.izumi.logstage.distage

import com.github.pshirshov.izumi.distage.model.definition.BootstrapModuleDef
import com.github.pshirshov.izumi.fundamentals.reflection.SafeType0
import com.github.pshirshov.izumi.fundamentals.typesafe.config.{ConfigReader, RuntimeConfigReaderCodecs}
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.rendering.RenderingPolicy
import com.github.pshirshov.izumi.logstage.config.codecs.RenderingPolicyCodec.RenderingPolicyMapper
import com.github.pshirshov.izumi.logstage.config.codecs.{LogstagePrimitiveCodecs, RenderingPolicyCodec}

import scala.reflect.runtime.{universe => ru}

class LogstageCodecsModule() extends BootstrapModuleDef {

  many[RenderingPolicyMapper[_ <: RenderingPolicy, _]]

  many[RuntimeConfigReaderCodecs].add {
    policyMappers: Set[RenderingPolicyMapper[_ <: RenderingPolicy, _]] =>
      val policyCodec = new RenderingPolicyCodec(policyMappers)

      new RuntimeConfigReaderCodecs {
        override val readerCodecs: Map[SafeType0[ru.type], ConfigReader[_]] = {
          Map(
            SafeType0.get[Log.Level] -> LogstagePrimitiveCodecs.logLevelCodec,
            SafeType0.get[RenderingPolicy] -> policyCodec,
          )
        }
      }
  }

}

