package com.github.pshirshov.izumi.logstage.config.modules

import com.github.pshirshov.izumi.distage.config.codec.RuntimeConfigReaderCodecs.RuntimeConfigReaderDefaultImpl
import com.github.pshirshov.izumi.distage.config.codec.{ConfigReader, RuntimeConfigReaderCodecs}
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.SafeType
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.logger.LogSink
import com.github.pshirshov.izumi.logstage.api.rendering.RenderingPolicy
import com.github.pshirshov.izumi.logstage.config.codecs.LogSinkCodec.LogSinkMapper
import com.github.pshirshov.izumi.logstage.config.codecs.RenderingPolicyCodec.RenderingPolicyMapper
import com.github.pshirshov.izumi.logstage.config.codecs.{LogSinkCodec, LogstagePrimitiveCodecs, RenderingPolicyCodec}
import scala.collection.JavaConverters._

class LogstageCodecsModule(logstageConfigPath: String) extends ModuleDef {

  many[RenderingPolicyMapper[_ <: RenderingPolicy]]

  many[LogSinkMapper[_ <: LogSink]]

  make[LogstageCodecs].from {
    (
      policyMappers: Set[RenderingPolicyMapper[_ <: RenderingPolicy]]
      , sinksMappers: Set[LogSinkMapper[_ <: LogSink]]
      , appConfig: AppConfig
    ) =>

      // I known that config reader must be pure and stateless, but i dunno hot resolve two issues:
      // - prevent from instantiate reduntant instances
      // - pass rendering policy as parameter to LogSink

      // TODO: smells like a shit but works...
      val policyCodec = new RenderingPolicyCodec(policyMappers)
      appConfig.config.getList(s"$logstageConfigPath.renderingPolicies").asScala.toList foreach policyCodec.apply

      LogstageCodecs(Map(
        SafeType.get[Log.Level] -> LogstagePrimitiveCodecs.logLevelCodec,
        SafeType.get[RenderingPolicy] -> new RenderingPolicyCodec(policyMappers),
        SafeType.get[LogSink] -> new LogSinkCodec(policyCodec, sinksMappers)
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

