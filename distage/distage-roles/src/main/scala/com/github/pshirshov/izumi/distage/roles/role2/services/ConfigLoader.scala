package com.github.pshirshov.izumi.distage.roles.role2.services

import distage.config.AppConfig

trait ConfigLoader {
  def buildConfig(): AppConfig
}
