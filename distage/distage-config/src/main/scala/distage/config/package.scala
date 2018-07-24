package distage

package object config {

  type AppConfig = com.github.pshirshov.izumi.distage.config.model.AppConfig
  val AppConfig: com.github.pshirshov.izumi.distage.config.model.AppConfig.type = com.github.pshirshov.izumi.distage.config.model.AppConfig

  type ConfigModule = com.github.pshirshov.izumi.distage.config.ConfigModule

  type AutoConf = com.github.pshirshov.izumi.distage.config.annotations.AutoConf
  type Conf = com.github.pshirshov.izumi.distage.config.annotations.Conf
  type ConfPath = com.github.pshirshov.izumi.distage.config.annotations.ConfPath

}
