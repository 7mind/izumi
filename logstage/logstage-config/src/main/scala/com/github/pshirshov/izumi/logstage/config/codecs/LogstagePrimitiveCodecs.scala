package com.github.pshirshov.izumi.logstage.config.codecs

import com.github.pshirshov.izumi.fundamentals.typesafe.config.ConfigReader
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.rendering.RenderingPolicy
import com.typesafe.config.{ConfigObject, ConfigValue}

import scala.util.Try

object LogstagePrimitiveCodecs {
  val logLevelCodec: ConfigReader[Log.Level] = ConfigReader.fromString[Log.Level](Log.Level.parse)
}
