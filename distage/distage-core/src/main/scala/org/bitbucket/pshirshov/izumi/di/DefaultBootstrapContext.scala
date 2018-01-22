package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.definition.Binding.SingletonBinding
import org.bitbucket.pshirshov.izumi.di.definition.{Binding, ImplDef, TrivialDIDef}
import org.bitbucket.pshirshov.izumi.di.model.exceptions.DIException
import org.bitbucket.pshirshov.izumi.di.model.{DIKey, EqualitySafeType}
import org.bitbucket.pshirshov.izumi.di.model.plan._
import org.bitbucket.pshirshov.izumi.di.planning._
import org.bitbucket.pshirshov.izumi.di.provisioning._
import org.bitbucket.pshirshov.izumi.di.reflection._


trait DefaultBootstrapContext extends Locator {
  override def parent: Option[Locator] = None

  // TODO: it's possible to make this safe with a macro

  private val symbolIntrospector = SymbolIntrospectorDefaultImpl.instance
  private val introspector = ProvisionerIntrospectorDefaultImpl.instance
  private val hook = ProvisionerHookDefaultImpl.instance

  private val ops = Seq(
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

  private val contextBindings = ops.foldLeft(List.empty[Binding]) {
    case (acc, op: ExecutableOp.WiringOp.ReferenceInstance) =>
      acc :+ SingletonBinding(op.target, ImplDef.InstanceImpl(op.wiring.instanceType, op.wiring.instance))

    case (acc, op: ExecutableOp.WiringOp.InstantiateClass) =>
      acc :+ SingletonBinding(op.target, ImplDef.TypeImpl(op.wiring.instanceType))

    case op =>
      throw new DIException(s"It's a bug! Bootstrap failed on unsupported operation $op", null)
  }

  private val contextDefinition = new TrivialDIDef(contextBindings)

  override def plan: FinalPlan = new FinalPlanImmutableImpl(ops, contextDefinition)

  private val bootstrapProducer = new ProvisionerDefaultImpl(hook, introspector)
  private val bootstrappedContext = bootstrapProducer.provision(plan, this)

  override protected def unsafeLookup(key: DIKey): Option[Any] = bootstrappedContext.get(key)
  override def enumerate: Stream[IdentifiedRef] = bootstrappedContext.enumerate

  private def bind[Key:Tag, I: Tag](instance: I): ExecutableOp = {
    ExecutableOp.WiringOp.ReferenceInstance(DIKey.get[Key], UnaryWiring.Instance(EqualitySafeType.get[I], instance))
  }

  private def bind[Key:Tag, Target:Tag]: ExecutableOp = {
    val targetType = EqualitySafeType.get[Target]
    val ctr = symbolIntrospector.selectConstructor(targetType)
    val context = DependencyContext.ConstructorParameterContext(targetType, ctr)

    val associations = ctr.arguments.map {
      param =>
        Association.Parameter(context, param, DIKey.TypeKey(EqualitySafeType(param.info)))

    }

    ExecutableOp.WiringOp.InstantiateClass(DIKey.get[Key], UnaryWiring.Constructor(targetType, ctr.constructorSymbol, associations))
  }
}

object DefaultBootstrapContext {
  final val instance: DefaultBootstrapContext = new DefaultBootstrapContext {}
}
