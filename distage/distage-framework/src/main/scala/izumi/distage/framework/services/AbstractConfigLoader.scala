package izumi.distage.framework.services

import izumi.distage.config.model.AppConfig

trait AbstractConfigLoader {
  def loadConfig(clue: String): AppConfig

  final def map(f: AppConfig => AppConfig): ConfigLoader = (clue: String) => f(loadConfig(clue))
}
