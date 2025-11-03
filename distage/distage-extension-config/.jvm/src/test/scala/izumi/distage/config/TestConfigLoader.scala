package izumi.distage.config

import com.typesafe.config.{ConfigFactory, ConfigParseOptions, ConfigResolveOptions}

object TestConfigLoader {
  def loadConfig(path: String): DistageConfigImpl = {
    ConfigFactory.load(path, ConfigParseOptions.defaults().setAllowMissing(false), ConfigResolveOptions.noSystem())
  }
}
