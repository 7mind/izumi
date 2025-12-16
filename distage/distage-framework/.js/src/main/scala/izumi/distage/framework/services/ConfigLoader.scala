package izumi.distage.framework.services

import izumi.distage.config.model.AppConfig

trait ConfigLoader {
  def loadConfig(clue: String): AppConfig

  final def map(f: AppConfig => AppConfig): ConfigLoader = (clue: String) => f(loadConfig(clue))
}

object ConfigLoader {
  def empty: ConfigLoader = _ => AppConfig.empty
}
