package com.github.pshirshov.izumi.distage.roles.role2

import com.github.pshirshov.izumi.distage.config.model.AppConfig

trait ConfigLoader {
  def buildConfig(): AppConfig
}

