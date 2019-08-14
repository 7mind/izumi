package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.BootstrapModuleDef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Tag
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook

abstract class AutoSetModule extends BootstrapModuleDef {
  def register[T: Tag]: AutoSetModule = {
    many[T]
    many[PlanningHook].add(new AssignableFromAutoSetHook[T, T](identity))
    this
  }

  def register[T: Tag, B: Tag](wrap: T => B): AutoSetModule = {
    many[B]
    many[PlanningHook].add(new AssignableFromAutoSetHook[T, B](wrap))
    this
  }
}

object AutoSetModule {
  def apply(): AutoSetModule = new AutoSetModule {}
}
