package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.definition.Binding.SingletonBinding
import org.bitbucket.pshirshov.izumi.di.definition.{Binding, ImplDef, TrivialDIDef}
import org.bitbucket.pshirshov.izumi.di.model.exceptions.DIException
import org.bitbucket.pshirshov.izumi.di.model.{DIKey, EqualitySafeType}
import org.bitbucket.pshirshov.izumi.di.model.plan._
import org.bitbucket.pshirshov.izumi.di.planning._
import org.bitbucket.pshirshov.izumi.di.provisioning.{Provisioner, ProvisionerDefaultImpl}
import org.bitbucket.pshirshov.izumi.di.reflection._


trait DefaultBootstrapContext extends Locator {
  override def parent: Option[Locator] = None

  // TODO: it's possible to make this safe with a macro

  private val symbolIntrospector = SymbolIntrospectorDefaultImpl.instance

  private val ops = Seq(
    bindInstance[CustomOpHandler, CustomOpHandler.NullCustomOpHander.type](CustomOpHandler.NullCustomOpHander)
    , bindInstance[LookupInterceptor, NullLookupInterceptor](NullLookupInterceptor.instance)
    , bindInstance[PlanningHook, PlanningHookDefaultImpl](PlanningHookDefaultImpl.instance)
    , bindInstance[SymbolIntrospector, SymbolIntrospectorDefaultImpl](symbolIntrospector)

    , bindSubclass[Provisioner, ProvisionerDefaultImpl]
    , bindSubclass[PlanningObsever, PlanningObserverDefaultImpl]
    , bindSubclass[PlanResolver, PlanResolverDefaultImpl]
    , bindSubclass[DependencyKeyProvider, DependencyKeyProviderDefaultImpl]
    , bindSubclass[PlanAnalyzer, PlanAnalyzerDefaultImpl]
    , bindSubclass[PlanMergingPolicy, PlanMergingPolicyDefaultImpl]

    , bindSubclass[TheFactoryOfAllTheFactories, TheFactoryOfAllTheFactoriesDefaultImpl](Seq(DIKey.get[Provisioner]))
    , bindSubclass[ForwardingRefResolver, ForwardingRefResolverDefaultImpl](Seq(DIKey.get[PlanAnalyzer]))
    , bindSubclass[SanityChecker, SanityCheckerDefaultImpl](Seq(DIKey.get[PlanAnalyzer]))
    , bindSubclass[ReflectionProvider, ReflectionProviderDefaultImpl](Seq(
      DIKey.get[DependencyKeyProvider]
      , DIKey.get[SymbolIntrospector]
    ))

    , bindSubclass[Planner, PlannerDefaultImpl](Seq(
      DIKey.get[PlanResolver]
      , DIKey.get[ForwardingRefResolver]
      , DIKey.get[ReflectionProvider]
      , DIKey.get[SanityChecker]
      , DIKey.get[CustomOpHandler]
      , DIKey.get[PlanningObsever]
      , DIKey.get[PlanMergingPolicy]
      , DIKey.get[PlanningHook]
    ))

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

  private val bootstrapProducer = new ProvisionerDefaultImpl()
  private val bootstrappedContext = bootstrapProducer.provision(plan, this)

  override protected def unsafeLookup(key: DIKey): Option[Any] = bootstrappedContext.get(key)
  override def enumerate: Stream[IdentifiedRef] = bootstrappedContext.enumerate

  private def bindInstance[Key:Tag, I: Tag](instance: I): ExecutableOp = {
    ExecutableOp.WiringOp.ReferenceInstance(DIKey.get[Key], UnaryWiring.Instance(EqualitySafeType.get[I], instance))
  }

  private def bindSubclass[Key:Tag, Target:Tag]: ExecutableOp =  bindSubclass[Key, Target](Seq.empty)
  
  private def bindSubclass[Key:Tag, Target:Tag](paramKeys: Seq[DIKey]): ExecutableOp = {
    val targetType = EqualitySafeType.get[Target]
    val ctr = symbolIntrospector.selectConstructor(targetType)
    val constructor = ctr.arguments.toSet
    val context = DependencyContext.ConstructorParameterContext(targetType, ctr)

    val associations = paramKeys.map {
      param =>
        val head = constructor.find(_.typeSignature.baseClasses.contains(param.symbol.tpe.typeSymbol)).head
        Association.Parameter(context, head, param)
    }

    ExecutableOp.WiringOp.InstantiateClass(DIKey.get[Key], UnaryWiring.Constructor(targetType, ctr.constructorSymbol, associations))
  }
}

object DefaultBootstrapContext {
  final val instance: DefaultBootstrapContext = new DefaultBootstrapContext {}
}
