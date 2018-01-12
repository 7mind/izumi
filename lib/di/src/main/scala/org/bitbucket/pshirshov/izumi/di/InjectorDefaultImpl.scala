package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.definition.ContextDefinition
import org.bitbucket.pshirshov.izumi.di.model.plan.FinalPlan

/**
  * TODO:
  * - identified (named) bindings => DependencyKeyProviderDefaultImpl
  *
  * - instantiation logic => InjectorDefaultImpl#produce
  * - instantiation logic: provide classloader policies from outside
  *
  * - full test coverage
  *
  * - expose Definitions and Plans in Contexts
  *
  * + Context enumeration
  * + multibindings
  * + identified (named) bindings => getters
  * + identified (named) bindings => DSL
  * + remove mirror ref
  * + refactor the rest of the traits
  * + strategies as parent injector values
  * + sanity check: reference completeness
  * + sanity checks: partial order
  * + circulars: outside of resolver
  * + extension point: custom op
  * + factories: filter parameters out of products
  */

class InjectorDefaultImpl(parentContext: Locator) extends Injector {
  override def plan(context: ContextDefinition): FinalPlan = {
    parentContext.get[Planner].plan(context)
  }

  override def produce(diPlan: FinalPlan): Locator = {
    parentContext.get[TheFactoryOfAllTheFactories].produce(diPlan, parentContext)
  }
}
