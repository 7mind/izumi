package izumi.distage.config.model

import com.typesafe.config.ConfigFactory

trait AppConfigSyntax {
  val empty: AppConfig = AppConfig(ConfigFactory.empty())
}
