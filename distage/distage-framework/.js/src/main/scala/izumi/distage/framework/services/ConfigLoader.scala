package izumi.distage.framework.services

import distage.config.AppConfig

trait ConfigLoader extends AbstractConfigLoader

object ConfigLoader {
  def empty: ConfigLoader = (_: String) => AppConfig.empty
}
