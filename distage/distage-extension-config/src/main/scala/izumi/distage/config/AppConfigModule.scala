package izumi.distage.config

import izumi.distage.config.model.AppConfig
import izumi.distage.model.definition.ModuleDef

class AppConfigModule(appConfig: AppConfig) extends ModuleDef {
  make[AppConfig].fromValue(appConfig).exposed
}

object AppConfigModule {
  def apply(appConfig: AppConfig): AppConfigModule = new AppConfigModule(appConfig)
  def apply(config: DistageConfigImpl): AppConfigModule = new AppConfigModule(AppConfig.provided(config))
}
