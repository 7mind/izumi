package com.github.pshirshov.izumi.logstage.config.modules

import com.github.pshirshov.izumi.distage.config.codec.RuntimeConfigReaderCodecs.RuntimeConfigReaderDefaultImpl
import com.github.pshirshov.izumi.distage.config.codec.{ConfigReader, RuntimeConfigReaderCodecs}
import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.SafeType
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.rendering.RenderingPolicy
import com.github.pshirshov.izumi.logstage.config.codecs.RenderingPolicyCodec.RenderingPolicyMapper
import com.github.pshirshov.izumi.logstage.config.codecs.{LogstagePrimitiveCodecs, RenderingPolicyCodec}

class LogstageCodecsModule extends ModuleDef {

  many[RenderingPolicyMapper[_ <: RenderingPolicy]]

  make[LogstageCodecs].from {
    policyMappers: Set[RenderingPolicyMapper[_ <: RenderingPolicy]] =>
      LogstageCodecs(Map(
        SafeType.get[Log.Level] -> LogstagePrimitiveCodecs.logLevelCodec,
        SafeType.get[RenderingPolicy] -> new RenderingPolicyCodec(policyMappers)
      ))
  }

  many[RuntimeConfigReaderCodecs].add {
    codecs: LogstageCodecs =>
      new RuntimeConfigReaderCodecs {
        override val readerCodecs: Map[SafeType, ConfigReader[_]] = {
          RuntimeConfigReaderDefaultImpl.readerCodecs ++ codecs.codecs

          // TODO: we can generate SateType -> ConfigReader[_] automatically
          //          ++ codecs.codecs
          //            .foldLeft(Map.empty[SafeType, ConfigReader[_]]) {
          //              case (acc, codec) =>
          //                val kv = ConfigReader.toKeyValue(codec)
          //                acc ++ Map(kv._1 -> kv._2)
          //            }
          //        }
        }
      }
  }
}

case class LogstageCodecs(codecs: Map[SafeType, ConfigReader[_]])

