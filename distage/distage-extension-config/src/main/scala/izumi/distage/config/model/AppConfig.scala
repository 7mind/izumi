package izumi.distage.config.model

import izumi.distage.config.DistageConfigImpl

final case class AppConfig(config: DistageConfigImpl)

object AppConfig extends AppConfigSyntax {}
