package com.github.pshirshov.izumi.distage.model.reflection.universe

import scala.reflect.macros.blackbox

object StaticDIUniverse {
  def apply(c: blackbox.Context): StaticDIUniverse[c.universe.type] = new DIUniverse {
    override val u: c.universe.type = c.universe
  }
}
