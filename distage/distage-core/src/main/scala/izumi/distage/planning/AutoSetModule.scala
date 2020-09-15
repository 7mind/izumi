package izumi.distage.planning

import izumi.distage.model.definition.BootstrapModuleDef
import izumi.reflect.Tag
import izumi.distage.model.planning.PlanningHook

/**
  * Auto-Sets collect all bindings with static types of _implementations_
  * that are `_ <: T` into a summonable `Set[T]`
  *
  * @see [[AutoSetHook]]
  * @see same concept in MacWire: https://github.com/softwaremill/macwire#multi-wiring-wireset
  */
abstract class AutoSetModule extends BootstrapModuleDef {
  def register[T: Tag]: AutoSetModule = {
    many[T]
    many[PlanningHook].add(new AutoSetHook[T, T])
    this
  }
}

object AutoSetModule {
  def apply(): AutoSetModule = new AutoSetModule {}
}
