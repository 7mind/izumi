package distage.config

import com.github.pshirshov.izumi.distage.config.model
import com.github.pshirshov.izumi.distage.config.annotations

trait DistageConfig {

  type AppConfig = model.AppConfig
  val AppConfig: model.AppConfig.type = model.AppConfig

  type ConfigModule = ConfigModule

  type AutoConf = annotations.AutoConf
  type Conf = annotations.Conf
  type ConfPath = annotations.ConfPath

}
