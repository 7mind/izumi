package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.definition.TrivialDIDef
import org.bitbucket.pshirshov.izumi.di.model.{DIKey, EqualitySafeType}
import org.bitbucket.pshirshov.izumi.di.model.plan.{Association, ExecutableOp, FinalPlan, FinalPlanImmutableImpl}
import org.bitbucket.pshirshov.izumi.di.planning._
import org.bitbucket.pshirshov.izumi.di.provisioning.{Provisioner, ProvisionerDefaultImpl}
import org.bitbucket.pshirshov.izumi.di.reflection.{DependencyKeyProvider, DependencyKeyProviderDefaultImpl, ReflectionProvider, ReflectionProviderDefaultImpl}


trait DefaultBootstrapContext extends Locator {
  override def parent: Option[Locator] = None

  // TODO: it's possible to make this safe with a macro
  
  private val ops = Seq(
    bindInstance[CustomOpHandler, CustomOpHandler.NullCustomOpHander.type](CustomOpHandler.NullCustomOpHander)
    , bindInstance[LookupInterceptor, NullLookupInterceptor](NullLookupInterceptor.instance)

    , bindSubclass[Provisioner, ProvisionerDefaultImpl]
    , bindSubclass[PlanningObsever, PlanningObserverDefaultImpl]
    , bindSubclass[PlanResolver, PlanResolverDefaultImpl]
    , bindSubclass[DependencyKeyProvider, DependencyKeyProviderDefaultImpl]
    , bindSubclass[PlanAnalyzer, PlanAnalyzerDefaultImpl]
    , bindSubclass[PlanMergingPolicy, PlanMergingPolicyDefaultImpl]

    , bindSubclass[TheFactoryOfAllTheFactories, TheFactoryOfAllTheFactoriesDefaultImpl](Seq(DIKey.get[Provisioner]))
    , bindSubclass[ForwardingRefResolver, ForwardingRefResolverDefaultImpl](Seq(DIKey.get[PlanAnalyzer]))
    , bindSubclass[SanityChecker, SanityCheckerDefaultImpl](Seq(DIKey.get[PlanAnalyzer]))
    , bindSubclass[ReflectionProvider, ReflectionProviderDefaultImpl](Seq(DIKey.get[DependencyKeyProvider]))

    , bindSubclass[Planner, PlannerDefaultImpl](Seq(
      DIKey.get[PlanResolver]
      , DIKey.get[ForwardingRefResolver]
      , DIKey.get[ReflectionProvider]
      , DIKey.get[SanityChecker]
      , DIKey.get[CustomOpHandler]
      , DIKey.get[PlanningObsever]
      , DIKey.get[PlanMergingPolicy]
    ))

  )


  private val contextDefinition = TrivialDIDef(Seq.empty) // TODO: fill
  override def plan: FinalPlan = new FinalPlanImmutableImpl(ops, definition = contextDefinition)

  private val bootstrapProducer = new ProvisionerDefaultImpl()
  private val bootstrappedContext = bootstrapProducer.provision(plan, this)

  override protected def unsafeLookup(key: DIKey): Option[Any] = bootstrappedContext.get(key)
  override def enumerate: Stream[IdentifiedRef] = bootstrappedContext.map(IdentifiedRef.tupled).toStream

  private def bindInstance[Key:Tag, I: Tag](instance: I): ExecutableOp = {
    ExecutableOp.ReferenceInstance(DIKey.get[Key], EqualitySafeType.get[I], instance)
  }

  private def bindSubclass[Key:Tag, Target:Tag]: ExecutableOp =  bindSubclass[Key, Target](Seq.empty)
  
  private def bindSubclass[Key:Tag, Target:Tag](paramKeys: Seq[DIKey]): ExecutableOp = {
    val targetType = EqualitySafeType.get[Target]
    val constructor = ReflectionProviderDefaultImpl.selectConstructor(targetType).toSet
    val associations = paramKeys.map {
      param =>
        Association.Parameter(constructor.find(_.info.baseClasses.contains(param.symbol.symbol.typeSymbol)).head, param)
    }

    ExecutableOp.InstantiateClass(DIKey.get[Key], targetType, associations)
  }
}

object DefaultBootstrapContext {
  final val instance: DefaultBootstrapContext = new DefaultBootstrapContext {}
}
