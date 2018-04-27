package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model._
import com.github.pshirshov.izumi.distage.model.definition.BindingT.SingletonBinding
import com.github.pshirshov.izumi.distage.model.definition._
import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.provisioning.Provisioner
import com.github.pshirshov.izumi.distage.model.references.IdentifiedRef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse.Wiring.UnaryWiring
import com.github.pshirshov.izumi.distage.provisioning._
import com.github.pshirshov.izumi.distage.provisioning.strategies._
import com.github.pshirshov.izumi.distage.reflection._

class BootstrapPlanner extends Planner {
  override def plan(context: ContextDefinition): FinalPlan = {
    FinalPlanImmutableImpl(context) {
      context.bindings.foldLeft(Seq.empty[ExecutableOp]) {
        case (acc, SingletonBinding(target, ImplDef.TypeImpl(impl))) =>
          val materials = ReflectionProviderDefaultImpl.Java.instance.constructorParameters(impl)
          acc :+ WiringOp.InstantiateClass(target, UnaryWiring.Constructor(impl, materials))

        case (acc, SingletonBinding(target, ImplDef.InstanceImpl(impl, instance))) =>
          acc :+ WiringOp.ReferenceInstance(target, UnaryWiring.Instance(impl, instance))

        case op =>
          throw new DIException(s"It's a bug! Bootstrap failed on unsupported definition: $op", null)
      }
    }
  }
}

class DefaultBootstrapContext(contextDefinition: ContextDefinition) extends AbstractLocator {
  private lazy val bootstrapProducer = new ProvisionerDefaultImpl(
    ProvisionerHookDefaultImpl.instance
    , ProvisionerIntrospectorDefaultImpl.instance
    // TODO: add user-controllable logs
    , LoggerHookDefaultImpl.instance
    , SetStrategyDefaultImpl.instance
    , ProxyStrategyDefaultImpl.instance
    , FactoryStrategyDefaultImpl.instance
    , TraitStrategyDefaultImpl.instance
    , ProviderStrategyDefaultImpl.instance
    , ClassStrategyDefaultImpl.instance
    , ImportStrategyDefaultImpl.instance
    , CustomStrategyDefaultImpl.instance
    , InstanceStrategyDefaultImpl.instance
  )

  val immutableDefinitions: ContextDefinition = TrivialDIDef
    .instance[Provisioner](bootstrapProducer)


  val fullDefinition = TrivialDIDef(immutableDefinitions.bindings ++ contextDefinition.bindings)

  // we don't need to pass all these instances, but why create new ones in case we have them already?
  private lazy val bootstrappedContext = bootstrapProducer.provision(plan, this)

  override protected def unsafeLookup(key: RuntimeUniverse.DIKey): Option[Any] = bootstrappedContext.get(key)

  override lazy val parent: Option[AbstractLocator] = None
  override lazy val plan: FinalPlan = new BootstrapPlanner().plan(fullDefinition)

  override def enumerate: Stream[IdentifiedRef] = bootstrappedContext.enumerate
}

