package izumi.distage.config.model

import izumi.distage.config.DistageConfigImpl

final case class AppConfig(
  config: DistageConfigImpl
)

object AppConfig {
  val empty: AppConfig = AppConfig(Map.empty[String, String])
  def provided(config: DistageConfigImpl): AppConfig = AppConfig(config)
}
