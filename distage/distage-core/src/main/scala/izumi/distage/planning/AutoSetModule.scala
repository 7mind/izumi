package izumi.distage.planning

import izumi.distage.model.definition.{BootstrapModuleDef, Identifier}
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
open class AutoSetModule(setName: Option[Identifier]) extends BootstrapModuleDef {
  def this() = this(None)

  def register[T: Tag](weak: Boolean): this.type = {
    registerOnly[T](InclusionPredicate.IncludeAny, weak)
  }

  def registerOnly[T: Tag](filter: InclusionPredicate, weak: Boolean): this.type = {
    setName match {
      case Some(id) =>
        many[T].named(id).exposed
        many[PlanningHook].addValue(AutoSetHook[T](id)(weak, filter))

      case None =>
        many[T].exposed
        many[PlanningHook].addValue(AutoSetHook[T](weak, filter))
    }
    this
  }
}

object AutoSetModule {
  def apply(): AutoSetModule = new AutoSetModule(None)
  def apply(setName: Identifier): AutoSetModule = new AutoSetModule(Some(setName))
}
