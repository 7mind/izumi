package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.definition.TrivialDIDef
import com.github.pshirshov.izumi.distage.model._
import com.github.pshirshov.izumi.distage.model.definition.Binding.SingletonBinding
import com.github.pshirshov.izumi.distage.model.definition.{Binding, ImplDef}
import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.github.pshirshov.izumi.distage.model.plan.Wiring.UnaryWiring
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.planning._
import com.github.pshirshov.izumi.distage.model.provisioning.Provisioner
import com.github.pshirshov.izumi.distage.model.references.{DIKey, IdentifiedRef}
import com.github.pshirshov.izumi.distage.model.reflection.{DependencyKeyProvider, ReflectionProvider}
import com.github.pshirshov.izumi.distage.planning._
import com.github.pshirshov.izumi.distage.provisioning._
import com.github.pshirshov.izumi.distage.provisioning.strategies._
import com.github.pshirshov.izumi.distage.reflection._
import com.github.pshirshov.izumi.fundamentals.reflection.{EqualitySafeType, _}



trait DefaultBootstrapContext extends AbstractLocator {
  override def parent: Option[AbstractLocator] = None

  private val bootstrapProducer = new ProvisionerDefaultImpl(
    ProvisionerHookDefaultImpl.instance
    , ProvisionerIntrospectorDefaultImpl.instance
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

  // we don't need to pass all these instances, though why to create a new one in case we have one already?
  private val contextBindings = Seq(
    bind[CustomOpHandler, CustomOpHandler.NullCustomOpHander.type](CustomOpHandler.NullCustomOpHander)
    , bind[LookupInterceptor, NullLookupInterceptor](NullLookupInterceptor.instance)
    , bind[SymbolIntrospector, SymbolIntrospectorDefaultImpl](SymbolIntrospectorDefaultImpl.instance)
    , bind[Provisioner, ProvisionerDefaultImpl](bootstrapProducer)
    , bind[PlanningHook, PlanningHookDefaultImpl]
    , bind[PlanningObsever, PlanningObserverDefaultImpl]
    , bind[PlanResolver, PlanResolverDefaultImpl]
    , bind[DependencyKeyProvider, DependencyKeyProviderDefaultImpl]
    , bind[PlanAnalyzer, PlanAnalyzerDefaultImpl]
    , bind[PlanMergingPolicy, PlanMergingPolicyDefaultImpl]
    , bind[TheFactoryOfAllTheFactories, TheFactoryOfAllTheFactoriesDefaultImpl]
    , bind[ForwardingRefResolver, ForwardingRefResolverDefaultImpl]
    , bind[SanityChecker, SanityCheckerDefaultImpl]
    , bind[ReflectionProvider, ReflectionProviderDefaultImpl]
    , bind[Planner, PlannerDefaultImpl]
  )

  private val ops = contextBindings.foldLeft(Seq.empty[ExecutableOp]) {
    case (acc, SingletonBinding(target, ImplDef.TypeImpl(impl))) =>
      val ctr = SymbolIntrospectorDefaultImpl.instance.selectConstructor(impl)
      val context = DependencyContext.ConstructorParameterContext(target.symbol, ctr)

      val associations = ctr.arguments.map {
        param =>
          Association.Parameter(context, param, DIKey.TypeKey(RuntimeUniverse.SafeType(param.info)))

      }
      acc :+ ExecutableOp.WiringOp.InstantiateClass(target, UnaryWiring.Constructor(impl, ctr.constructorSymbol, associations))

    case (acc, SingletonBinding(target, ImplDef.InstanceImpl(impl, instance))) =>
      acc :+ ExecutableOp.WiringOp.ReferenceInstance(target, UnaryWiring.Instance(impl, instance))

    case op =>
      throw new DIException(s"It's a bug! Bootstrap failed on unsupported definition: $op", null)
  }

  private val contextDefinition = new TrivialDIDef(contextBindings)
  override val plan: FinalPlan = new FinalPlanImmutableImpl(ops, contextDefinition)

  private val bootstrappedContext = bootstrapProducer.provision(plan, this)

  override protected def unsafeLookup(key: DIKey): Option[Any] = bootstrappedContext.get(key)
  override def enumerate: Stream[IdentifiedRef] = bootstrappedContext.enumerate

  private def bind[Key:RuntimeUniverse.Tag, I: RuntimeUniverse.Tag](instance: I): Binding= {
    SingletonBinding(DIKey.get[Key], ImplDef.InstanceImpl(EqualitySafeType.get[I], instance))
  }

  private def bind[Key:RuntimeUniverse.Tag, Target:RuntimeUniverse.Tag]: Binding = {
    SingletonBinding(DIKey.get[Key], ImplDef.TypeImpl(EqualitySafeType.get[Target]))
  }
}

object DefaultBootstrapContext {
  final val instance: DefaultBootstrapContext = new DefaultBootstrapContext {}
}
