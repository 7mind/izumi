package com.github.pshirshov.izumi.logstage.config.codecs

import com.github.pshirshov.izumi.distage.config.codec.ConfigReader
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.rendering.RenderingPolicy
import com.typesafe.config.ConfigValue

import scala.util.Try

object LogstagePrimitiveCodecs {
  val logLevelCodec: ConfigReader[Log.Level] = ConfigReader.fromString[Log.Level](Log.Level.parse)
  val policyConfigCodec: ConfigReader[RenderingPolicy.PolicyConfig] = new ConfigReader[RenderingPolicy.PolicyConfig] {
    override def apply(configValue: ConfigValue): Try[RenderingPolicy.PolicyConfig] = {
      for {
        cfg <- Try(configValue.atKey("key").getConfig("key"))
        withExceptions <- Try(cfg.getBoolean("withExceptions"))
        withColor <- Try(cfg.getBoolean("withColor"))
        prettyPrint <- Try(cfg.getBoolean("prettyPrint"))
        renderingLayout = Try(cfg.getString("renderingLayout")).toOption
      } yield RenderingPolicy.PolicyConfig(withColor, withExceptions, prettyPrint, renderingLayout)
    }
  }
}
