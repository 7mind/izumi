package com.github.pshirshov.izumi.fundamentals.reflection

import com.github.pshirshov.izumi.fundamentals.reflection.RuntimeUniverse.u

import scala.reflect.api.JavaUniverse
import scala.reflect.runtime.universe

object RuntimeUniverse extends DIUniverse {
  override final val u: JavaUniverse = scala.reflect.runtime.universe
  override val mirror: u.Mirror = scala.reflect.runtime.currentMirror.asInstanceOf[u.Mirror]
}
