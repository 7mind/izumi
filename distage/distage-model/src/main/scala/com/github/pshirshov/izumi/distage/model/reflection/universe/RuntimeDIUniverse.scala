package com.github.pshirshov.izumi.distage.model.reflection.universe

trait RuntimeDIUniverse
  extends DIUniverse
{
  override final val u: scala.reflect.runtime.universe.type = scala.reflect.runtime.universe

  val mirror: u.Mirror = scala.reflect.runtime.currentMirror
}

object RuntimeDIUniverse extends RuntimeDIUniverse
