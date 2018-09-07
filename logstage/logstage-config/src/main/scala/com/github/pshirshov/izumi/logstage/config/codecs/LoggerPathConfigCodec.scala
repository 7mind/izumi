package com.github.pshirshov.izumi.logstage.config.codecs

import com.github.pshirshov.izumi.fundamentals.typesafe.config.ConfigReader
import com.github.pshirshov.izumi.logstage.api.config.LoggerPathConfig
import com.github.pshirshov.izumi.logstage.api.logger.LogSink
import com.typesafe.config.{ConfigObject, ConfigValue, ConfigValueType}

import scala.collection.JavaConverters._
import scala.util.{Failure, Try}

class LoggerPathConfigCodec(sinks: Map[String, LogSink]) extends ConfigReader[LoggerPathConfig] {
  override def apply(configValue: ConfigValue): Try[LoggerPathConfig] = {
    configValue.valueType() match {
      case ConfigValueType.STRING =>
        for {
          threshold <- LogstagePrimitiveCodecs.logLevelCodec.apply(configValue)
          defaultSink <- Try(sinks("default"))
        } yield LoggerPathConfig(threshold, Seq(defaultSink))
      case ConfigValueType.OBJECT =>
        for {
          obj <- Try(configValue.asInstanceOf[ConfigObject].toConfig)
          loglevel <- LogstagePrimitiveCodecs.logLevelCodec.apply(obj.getValue("threshold"))
          sinks <- Try {
            val sinkLabels = obj.getList("sinks").unwrapped().asScala.toList.map(_.asInstanceOf[String]).toSet
            val (res, unknowns) = sinkLabels.partition(sinks.contains)
            if (unknowns.nonEmpty) {
              throw new IllegalArgumentException(s"Unknown sinks defined : ${unknowns.mkString(",")}")
            }
            sinks.collect {
              case (k, v) if res(k) => v
            }
          }
        } yield LoggerPathConfig(loglevel, sinks.toSeq)
      case _ =>
        Failure(new IllegalArgumentException(
          s"""
             |Wrong config type ${configValue.valueType()}.
             |Expected a string value (short version with default sink) or a config object with threshold and sinks""".stripMargin))
    }
  }
}
