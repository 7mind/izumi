package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.typesafe.config.ConfigValue

trait RuntimeConfigReader {
  def read(config: ConfigValue, tpe: TypeFull): Any
}
