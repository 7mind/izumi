package com.github.pshirshov.izumi.logstage.distage

import com.github.pshirshov.izumi.distage.model.definition.{BootstrapModuleDef, ModuleDefDSL}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.reflection.SafeType0
import com.github.pshirshov.izumi.fundamentals.typesafe.config.{ConfigReader, RuntimeConfigReaderCodecs}
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.logger.LogSink
import com.github.pshirshov.izumi.logstage.api.rendering.RenderingPolicy
import com.github.pshirshov.izumi.logstage.config.codecs.LogSinkCodec.LogSinkMapper
import com.github.pshirshov.izumi.logstage.config.codecs.RenderingPolicyCodec.RenderingPolicyMapper
import com.github.pshirshov.izumi.logstage.config.codecs.{LogSinkCodec, LogstagePrimitiveCodecs, RenderingPolicyCodec}

import scala.reflect.runtime.{universe => ru}

trait LogstageCodecsModule extends BootstrapModuleDef {

  many[RenderingPolicyMapper[_ <: RenderingPolicy, _]]
  many[LogSinkMapper[_ <: LogSink, _]]

  make[LogSinkCodec].from {
    logsinkMappers: Set[LogSinkMapper[_ <: LogSink, _]] =>
      new LogSinkCodec(logsinkMappers)
  }

  make[RenderingPolicyCodec].from {
    policyMappers: Set[RenderingPolicyMapper[_ <: RenderingPolicy, _]] =>
      new RenderingPolicyCodec(policyMappers)
  }

  many[RuntimeConfigReaderCodecs].add {
    (
      policyCodec: RenderingPolicyCodec,
      logSinkCodec: LogSinkCodec) =>

      new RuntimeConfigReaderCodecs {
        override val readerCodecs: Map[SafeType0[ru.type], ConfigReader[_]] = {
          Map(
            SafeType0.get[Log.Level] -> LogstagePrimitiveCodecs.logLevelCodec,
            SafeType0.get[RenderingPolicy] -> policyCodec,
            SafeType0.get[LogSink] -> logSinkCodec,
          )
        }
      }
  }

  def bindLogSinkMapper[T <: LogSink : ru.TypeTag, C: ru.TypeTag](f: C => T): Unit = {
    many[LogSinkMapper[_ <: LogSink, _]].add {
      policyMappers: Set[RenderingPolicyMapper[_ <: RenderingPolicy, _]] =>
        val policyCodec = new RenderingPolicyCodec(policyMappers)
        new LogSinkMapper[T, C] {
          override def apply(props: C): T = f(props)

          override def extraCodecs: Map[SafeType0[ru.type], ConfigReader[_]] = super.extraCodecs ++ Map(
            SafeType0.get[RenderingPolicy] -> policyCodec
          )
        }
    }
  }

  def bindRenderingPolicyMapper[T <: RenderingPolicy : ru.TypeTag, C: ru.TypeTag](f: C => T): Unit = {
    many[RenderingPolicyMapper[_ <: RenderingPolicy, _]].add {
      new RenderingPolicyMapper[T, C] {
        override def apply(props: C): T = {
          f(props)
        }
      }
    }
  }

}

