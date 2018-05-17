package com.github.pshirshov.izumi.distage.config.codec

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.typesafe.config.Config

trait RuntimeConfigReader {
  def readConfig(config: Config, tpe: TypeFull): Any
}
