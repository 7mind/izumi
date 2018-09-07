package com.github.pshirshov.izumi.logstage.config.codecs

import com.github.pshirshov.izumi.fundamentals.typesafe.config.ConfigReader
import com.github.pshirshov.izumi.logstage.api.Log

object LogstagePrimitiveCodecs {
  val logLevelCodec: ConfigReader[Log.Level] = ConfigReader.fromString[Log.Level](Log.Level.parse)
}
