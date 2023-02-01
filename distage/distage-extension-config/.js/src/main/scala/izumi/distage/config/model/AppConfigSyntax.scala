package izumi.distage.config.model

trait AppConfigSyntax {
  val empty: AppConfig = AppConfig(Map.empty[String, String])
}
