package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.definition.DIDef
import org.bitbucket.pshirshov.izumi.di.model.plan.ReadyPlan
import org.bitbucket.pshirshov.izumi.di.planning.{DefaultPlannerImpl, ForwardingRefResolver, PlanResolver}
import org.bitbucket.pshirshov.izumi.di.reflection.ReflectionProvider

trait Producer {
  def produce(dIPlan: ReadyPlan): Locator
}

trait Injector extends Planner with Producer {

}

object Injector {
  def make(bootstrapContext: Locator): Injector = {
    val planResolver = bootstrapContext.get[PlanResolver]
    val forwardingRefResolver = bootstrapContext.get[ForwardingRefResolver]
    val reflectionProvider = bootstrapContext.get[ReflectionProvider]

    val planner = new DefaultPlannerImpl(
      planResolver
      , forwardingRefResolver
      , reflectionProvider
    )
    new InjectorDefaultImpl(planner)
  }

  def make(): Injector = make(DefaultBootstrapContext)
}

class InjectorDefaultImpl(planner: Planner) extends Injector {
  override def plan(context: DIDef): ReadyPlan = planner.plan(context)

  override def produce(diPlan: ReadyPlan): Locator = ???
}