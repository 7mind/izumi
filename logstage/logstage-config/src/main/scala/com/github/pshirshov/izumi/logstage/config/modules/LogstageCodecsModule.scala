package com.github.pshirshov.izumi.logstage.config.modules

import com.github.pshirshov.izumi.distage.config.codec.{ConfigReader, RuntimeConfigReaderCodecs}
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.SafeType
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.config.LoggerPathConfig
import com.github.pshirshov.izumi.logstage.api.logger.LogSink
import com.github.pshirshov.izumi.logstage.api.rendering.RenderingPolicy
import com.github.pshirshov.izumi.logstage.config.codecs.LogSinkCodec.LogSinkMapper
import com.github.pshirshov.izumi.logstage.config.codecs.RenderingPolicyCodec.RenderingPolicyMapper
import com.github.pshirshov.izumi.logstage.config.codecs.{LogSinkCodec, LoggerConfigCodec, LogstagePrimitiveCodecs, RenderingPolicyCodec}

import scala.collection.JavaConverters._

class LogstageCodecsModule(logstageConfigPath: String) extends ModuleDef {

  many[RenderingPolicyMapper[RenderingPolicy]]

  many[LogSinkMapper[LogSink]]

  make[LogstageCodecs].from {
    (
      policyMappers: Set[RenderingPolicyMapper[RenderingPolicy]]
      , sinksMappers: Set[LogSinkMapper[LogSink]]
      , appConfig: AppConfig
    ) =>

      // TODO: smells like a shit but works...
      val policyCodec = new RenderingPolicyCodec(policyMappers, LogstagePrimitiveCodecs.policyConfigCodec)
      appConfig.config.getList(s"$logstageConfigPath.renderingPolicies").asScala.toList foreach policyCodec.apply
      val logSinkCodec = new LogSinkCodec(policyCodec, sinksMappers)
      appConfig.config.getList(s"$logstageConfigPath.sinks").asScala.toList foreach logSinkCodec.apply

      LogstageCodecs(Map(
        SafeType.get[Log.Level] -> LogstagePrimitiveCodecs.logLevelCodec,
        SafeType.get[RenderingPolicy.PolicyConfig] -> LogstagePrimitiveCodecs.policyConfigCodec,
        SafeType.get[RenderingPolicy] -> policyCodec,
        SafeType.get[LogSink] -> logSinkCodec,
        SafeType.get[LoggerPathConfig] -> new LoggerConfigCodec(logSinkCodec)
      ))
  }
  many[RuntimeConfigReaderCodecs].add {
    codecGroup: LogstageCodecs =>
      new RuntimeConfigReaderCodecs {
        override val readerCodecs: Map[SafeType, ConfigReader[_]] = {
          codecGroup.codecs
        }
      }
  }

}

case class LogstageCodecs(codecs: Map[SafeType, ConfigReader[_]])

