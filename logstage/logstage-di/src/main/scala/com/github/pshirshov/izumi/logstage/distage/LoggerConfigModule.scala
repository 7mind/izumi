package com.github.pshirshov.izumi.logstage.distage

import com.github.pshirshov.izumi.distage.config.annotations.ConfPath
import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.fundamentals.reflection.SafeType0
import com.github.pshirshov.izumi.fundamentals.typesafe.config.{RuntimeConfigReader, RuntimeConfigReaderDefaultImpl}
import com.github.pshirshov.izumi.logstage.api.config.{LoggerConfig, LoggerPathConfig}
import com.github.pshirshov.izumi.logstage.api.logger.LogSink
import com.github.pshirshov.izumi.logstage.config.codecs.LoggerPathConfigCodec
import com.github.pshirshov.izumi.logstage.distage.LoggerConfigModule.Entries
import com.typesafe.config.ConfigObject

@deprecated("Old logger config is inconvenient", "2018-11-05")
class LoggerConfigModule extends ModuleDef {
  make[LoggerConfig].from {
    (sinks: Entries @ConfPath("logstage"), logstageConfig: ConfigObject @ConfPath("logstage"), runtimeConfigReader: RuntimeConfigReader) =>
      val reader = new RuntimeConfigReaderDefaultImpl(runtimeConfigReader.codecs ++ Map(
        SafeType0.get[LoggerPathConfig] -> new LoggerPathConfigCodec(sinks.sinks)
      ))
      reader.readConfigAsCaseClass(logstageConfig.toConfig, SafeType0.get[LoggerConfig]).asInstanceOf[LoggerConfig]
  }
}

object LoggerConfigModule {
  case class Entries(sinks: Map[String, LogSink])
}
