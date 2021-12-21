package izumi.distage.config.model

import com.typesafe.config.{Config, ConfigFactory}

final case class AppConfig(config: Config)

object AppConfig {
  val empty: AppConfig = AppConfig(ConfigFactory.empty())
}
