package com.github.pshirshov.izumi.logstage.config.codecs

import com.github.pshirshov.izumi.distage.config.codec.ConfigReader
import com.github.pshirshov.izumi.logstage.api.Log

object LogstagePrimitiveCodecs {
  val logLevelCodec: ConfigReader[Log.Level] = ConfigReader.fromString[Log.Level](Log.Level.parse)
}
