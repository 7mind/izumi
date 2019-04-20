package com.github.pshirshov.izumi.distage.roles.services

import distage.config.AppConfig

trait ConfigLoader {
  def buildConfig(): AppConfig
}
