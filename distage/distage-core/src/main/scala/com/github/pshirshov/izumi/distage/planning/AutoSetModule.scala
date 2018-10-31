package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.BootstrapModuleDef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Tag
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook

class AutoSetModule extends BootstrapModuleDef {
  def register[T: Tag]: AutoSetModule = {
    many[T]
    many[PlanningHook].add(new AssignableFromAutoSetHook[T])
    this
  }
}

object AutoSetModule {
  def apply(): AutoSetModule = new AutoSetModule()
}
