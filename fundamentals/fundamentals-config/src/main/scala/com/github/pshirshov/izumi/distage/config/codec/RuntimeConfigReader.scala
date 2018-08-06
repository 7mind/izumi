package com.github.pshirshov.izumi.distage.config.codec

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.typesafe.config.{Config, ConfigValue}

trait RuntimeConfigReader {
  def readConfig(config: Config, tpe: SafeType): Any
  def readValue(config: ConfigValue, tpe: SafeType): Any
}
