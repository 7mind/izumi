package com.github.pshirshov.izumi.fundamentals.reflection

import scala.reflect.api.{JavaUniverse, Universe}

object RuntimeUniverse extends DIUniverse {
  override final val u: JavaUniverse = scala.reflect.runtime.universe
  override val mirror: u.Mirror = scala.reflect.runtime.currentMirror.asInstanceOf[u.Mirror]
  type SingletonUniverse = Universe with scala.Singleton
}
