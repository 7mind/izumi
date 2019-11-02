package distage.config

import izumi.distage.config
import izumi.distage.config.model
import izumi.distage.config.annotations

trait DistageConfig {

  type AppConfig = model.AppConfig
  val AppConfig: model.AppConfig.type = model.AppConfig

  type ConfigModule = config.ConfigModule

  type AutoConf = annotations.AutoConf
  type Conf = annotations.Conf
  type ConfPath = annotations.ConfPath

}
