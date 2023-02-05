package izumi.distage.planning

import izumi.distage.model.definition.BootstrapModuleDef
import izumi.distage.model.planning.PlanningHook
import izumi.distage.planning.AutoSetHook.InclusionPredicate
import izumi.reflect.Tag

/**
  * Auto-Sets collect all bindings with static types of _implementations_
  * that are `_ <: T` into a summonable `Set[T]`
  *
  * @see [[AutoSetHook]]
  * @see same concept in MacWire: https://github.com/softwaremill/macwire#multi-wiring-wireset
  */
abstract class AutoSetModule(name: Option[String]) extends BootstrapModuleDef {
  def register[T: Tag]: AutoSetModule = {
    registerOnly[T](InclusionPredicate.IncludeAny)
  }

  def registerOnly[T: Tag](filter: InclusionPredicate): AutoSetModule = {
    name match {
      case Some(value) =>
        many[T].named(value)
        many[PlanningHook].named(value).addValue(AutoSetHook[T](filter))

      case None =>
        many[T]
        many[PlanningHook].addValue(AutoSetHook[T](filter))
    }
    this
  }
}

object AutoSetModule {
  def apply(): AutoSetModule = new AutoSetModule(None) {}
  def apply(name: String): AutoSetModule = new AutoSetModule(Some(name)) {}
}
