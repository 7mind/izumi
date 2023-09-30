package izumi.distage.framework.services

import izumi.distage.config.model.AppConfig

trait AbstractConfigLoader {
  def loadConfig(): AppConfig

  final def map(f: AppConfig => AppConfig): ConfigLoader = () => f(loadConfig())
}
