package izumi.distage.planning

import izumi.distage.model.definition.BootstrapModuleDef
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.Tag
import izumi.distage.model.planning.PlanningHook

/**
  * Auto-Sets collect all bindings with static types <: `T`
  * into a summonable `Set[T]`
  *
  * @see same concept in MacWire: https://github.com/softwaremill/macwire#multi-wiring-wireset
  */
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
