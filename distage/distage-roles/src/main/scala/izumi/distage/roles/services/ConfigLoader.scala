package izumi.distage.roles.services

import distage.config.AppConfig

trait ConfigLoader {
  def buildConfig(): AppConfig
}
