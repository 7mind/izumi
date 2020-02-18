package izumi.distage.config

import com.typesafe.config.Config
import izumi.distage.config.model.AppConfig
import izumi.distage.model.definition.ModuleDef

class AppConfigModule(appConfig: AppConfig) extends ModuleDef {
  def this(config: Config) = this(AppConfig(config))

  make[AppConfig].fromValue(appConfig)
}
object AppConfigModule {
  def apply(appConfig: AppConfig): AppConfigModule = new AppConfigModule(appConfig)
  def apply(config: Config): AppConfigModule = new AppConfigModule(config)
}
