package org.bitbucket.pshirshov.izumi.distage

import org.bitbucket.pshirshov.izumi.distage.definition.Binding.SingletonBinding
import org.bitbucket.pshirshov.izumi.distage.definition.{Binding, ImplDef, TrivialDIDef}
import org.bitbucket.pshirshov.izumi.distage.model.exceptions.DIException
import org.bitbucket.pshirshov.izumi.distage.model.plan._
import org.bitbucket.pshirshov.izumi.distage.model.{DIKey, EqualitySafeType}
import org.bitbucket.pshirshov.izumi.distage.planning._
import org.bitbucket.pshirshov.izumi.distage.provisioning._
import org.bitbucket.pshirshov.izumi.distage.reflection._


trait DefaultBootstrapContext extends Locator {
  override def parent: Option[Locator] = None

  // TODO: it's possible to make this safe with a macro

  private val symbolIntrospector = SymbolIntrospectorDefaultImpl.instance
  private val introspector = ProvisionerIntrospectorDefaultImpl.instance
  private val hook = ProvisionerHookDefaultImpl.instance

  private val contextBindings = Seq(
    bind[CustomOpHandler, CustomOpHandler.NullCustomOpHander.type](CustomOpHandler.NullCustomOpHander)
    , bind[LookupInterceptor, NullLookupInterceptor](NullLookupInterceptor.instance)
    , bind[PlanningHook, PlanningHookDefaultImpl](PlanningHookDefaultImpl.instance)
    , bind[SymbolIntrospector, SymbolIntrospectorDefaultImpl](symbolIntrospector)
    , bind[ProvisionerHook, ProvisionerHookDefaultImpl](hook)
    , bind[ProvisionerIntrospector, ProvisionerIntrospectorDefaultImpl](introspector)

    , bind[Provisioner, ProvisionerDefaultImpl]
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
      val ctr = symbolIntrospector.selectConstructor(impl)
      val context = DependencyContext.ConstructorParameterContext(target.symbol, ctr)

      val associations = ctr.arguments.map {
        param =>
          Association.Parameter(context, param, DIKey.TypeKey(EqualitySafeType(param.info)))

      }
      acc :+ ExecutableOp.WiringOp.InstantiateClass(target, UnaryWiring.Constructor(impl, ctr.constructorSymbol, associations))

    case (acc, SingletonBinding(target, ImplDef.InstanceImpl(impl, instance))) =>
      acc :+ ExecutableOp.WiringOp.ReferenceInstance(target, UnaryWiring.Instance(impl, instance))

    case op =>
      throw new DIException(s"It's a bug! Bootstrap failed on unsupported definition: $op", null)
  }

  private val contextDefinition = new TrivialDIDef(contextBindings)
  override val plan: FinalPlan = new FinalPlanImmutableImpl(ops, contextDefinition)
  
  private val bootstrapProducer = new ProvisionerDefaultImpl(hook, introspector)
  private val bootstrappedContext = bootstrapProducer.provision(plan, this)

  override protected def unsafeLookup(key: DIKey): Option[Any] = bootstrappedContext.get(key)
  override def enumerate: Stream[IdentifiedRef] = bootstrappedContext.enumerate

  private def bind[Key:Tag, I: Tag](instance: I): Binding= {
    SingletonBinding(DIKey.get[Key], ImplDef.InstanceImpl(EqualitySafeType.get[I], instance))
  }

  private def bind[Key:Tag, Target:Tag]: Binding = {
    SingletonBinding(DIKey.get[Key], ImplDef.TypeImpl(EqualitySafeType.get[Target]))
  }
}

object DefaultBootstrapContext {
  final val instance: DefaultBootstrapContext = new DefaultBootstrapContext {}
}
