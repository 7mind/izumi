package com.github.pshirshov.izumi.logstage.distage

import com.github.pshirshov.izumi.distage.model.definition.BootstrapModuleDef
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.fundamentals.reflection.SafeType0
import com.github.pshirshov.izumi.fundamentals.typesafe.config.{ConfigReader, RuntimeConfigReaderCodecs}
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.logger.LogSink
import com.github.pshirshov.izumi.logstage.api.rendering.Renderer
import com.github.pshirshov.izumi.logstage.config.codecs.LogSinkCodec.{LogSinkMapper, LogSinkMapperImpl}
import com.github.pshirshov.izumi.logstage.config.codecs.RenderingPolicyCodec.{RenderingPolicyMapper, RenderingPolicyMapperImpl}
import com.github.pshirshov.izumi.logstage.config.codecs.{LogSinkCodec, LogstagePrimitiveCodecs, RenderingPolicyCodec}

import scala.reflect.runtime.{universe => ru}

trait LogstageCodecsModule extends BootstrapModuleDef {

  many[RenderingPolicyMapper[Renderer]]
  many[LogSinkMapper[LogSink]]

  make[LogSinkCodec].from {
    logsinkMappers: Set[LogSinkMapper[LogSink]] =>
      new LogSinkCodec(logsinkMappers)
  }

  make[RenderingPolicyCodec].from {
    policyMappers: Set[RenderingPolicyMapper[Renderer]] =>
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
            SafeType0.get[Renderer] -> policyCodec,
            SafeType0.get[LogSink] -> logSinkCodec,
          )
        }
      }
  }

  def bindLogSinkMapper[T <: LogSink : ru.TypeTag, C: ru.TypeTag](f: C => T): Unit = {
    many[LogSinkMapper[LogSink]].add {
      policyMappers: Set[RenderingPolicyMapper[Renderer]] =>
        val policyCodec = new RenderingPolicyCodec(policyMappers)
        new LogSinkMapperImpl[T, C] {
          override def apply(props: C): T = f(props)

          override def extraCodecs: Map[SafeType0[ru.type], ConfigReader[_]] = super.extraCodecs ++ Map(
            SafeType0.get[Renderer] -> policyCodec
          )
        }
    }.discard()
  }

  def bindRenderingPolicyMapper[T <: Renderer : ru.TypeTag, C: ru.TypeTag](f: C => T): Unit = {
    many[RenderingPolicyMapper[Renderer]].add {
      new RenderingPolicyMapperImpl[T, C] {
        override def apply(props: C): T = {
          f(props)
        }
      }
    }.discard()
  }

}

