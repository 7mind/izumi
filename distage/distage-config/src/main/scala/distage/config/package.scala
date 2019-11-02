package distage

import izumi.distage.config.model
import izumi.distage.config.annotations

package object config extends DistageConfig {

  override type AppConfig = model.AppConfig
  override val AppConfig: model.AppConfig.type = model.AppConfig

  override type ConfigModule = izumi.distage.config.ConfigModule

  override type AutoConf = annotations.AutoConf
  override type Conf = annotations.Conf
  override type ConfPath = annotations.ConfPath

}
