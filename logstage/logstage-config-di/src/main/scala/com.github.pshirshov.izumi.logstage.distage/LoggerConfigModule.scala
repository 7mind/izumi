package com.github.pshirshov.izumi.logstage.distage

import com.github.pshirshov.izumi.distage.config.annotations.ConfPath
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.fundamentals.reflection.SafeType0
import com.github.pshirshov.izumi.fundamentals.typesafe.config.{RuntimeConfigReader, RuntimeConfigReaderDefaultImpl}
import com.github.pshirshov.izumi.logstage.api.config.{LoggerConfig, LoggerPathConfig}
import com.github.pshirshov.izumi.logstage.api.logger.LogSink
import com.github.pshirshov.izumi.logstage.config.codecs.LoggerPathConfigCodec

class LoggerConfigModule() extends ModuleDef {
  make[LoggerConfig].from {
    (sinks : Entries @ConfPath("logstage"), appConfig: AppConfig, runtimeConfigReader: RuntimeConfigReader) =>
      val reader = new RuntimeConfigReaderDefaultImpl(runtimeConfigReader.codecs ++ Map(
        SafeType0.get[LoggerPathConfig] -> new LoggerPathConfigCodec(sinks.sinks)
      ))
      val config = appConfig.config.getConfig("logstage")
      reader.readConfigAsCaseClass(config, SafeType0.get[LoggerConfig]).asInstanceOf[LoggerConfig]
  }
}

case class Entries(sinks : Map[String, LogSink])
