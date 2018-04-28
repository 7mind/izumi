package com.github.pshirshov.izumi.distage.bootstrap

import com.github.pshirshov.izumi.distage._
import com.github.pshirshov.izumi.distage.model._
import com.github.pshirshov.izumi.distage.model.definition._
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.planning._
import com.github.pshirshov.izumi.distage.model.provisioning._
import com.github.pshirshov.izumi.distage.model.provisioning.strategies._
import com.github.pshirshov.izumi.distage.model.references.IdentifiedRef
import com.github.pshirshov.izumi.distage.model.reflection.{DependencyKeyProvider, ReflectionProvider, SymbolIntrospector}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.planning._
import com.github.pshirshov.izumi.distage.provisioning._
import com.github.pshirshov.izumi.distage.provisioning.strategies._
import com.github.pshirshov.izumi.distage.reflection._
import com.github.pshirshov.izumi.fundamentals.platform.console.TrivialLogger


class DefaultBootstrapContext(contextDefinition: ContextDefinition) extends AbstractLocator {

  import DefaultBootstrapContext._

  // we don't need to pass all these instances, but why create new ones in case we have them already?
  protected lazy val bootstrappedContext: ProvisionImmutable = {
    bootstrapProducer.provision(plan, this)
  }

  protected def unsafeLookup(key: RuntimeDIUniverse.DIKey): Option[Any] = bootstrappedContext.get(key)

  lazy val parent: Option[AbstractLocator] = None
  lazy val plan: FinalPlan = bootstrapPlanner.plan(contextDefinition)

  def enumerate: Stream[IdentifiedRef] = bootstrappedContext.enumerate
}

object DefaultBootstrapContext {
  private lazy val bootstrapPlanner = {
    val reflectionProvider = new ReflectionProviderDefaultImpl.Runtime(
      new DependencyKeyProviderDefaultImpl.Runtime
      , new SymbolIntrospectorDefaultImpl.Runtime
    )

    val bootstrapObserver = new BootstrapPlanningObserver(TrivialLogger.make[DefaultBootstrapContext]("izumi.distage.debug.bootstrap"))

    val analyzer = new PlanAnalyzerDefaultImpl

    new PlannerDefaultImpl(
      new PlanResolverDefaultImpl
      , new ForwardingRefResolverDefaultImpl(analyzer)
      , reflectionProvider
      , new SanityCheckerDefaultImpl(analyzer)
      , CustomOpHandler.NullCustomOpHander
      , bootstrapObserver
      , new PlanMergingPolicyDefaultImpl(analyzer)
      , new PlanningHookDefaultImpl
    )
  }


  private lazy val bootstrapProducer = {
    val loggerHook = new LoggerHookDefaultImpl // TODO: add user-controllable logs

    new ProvisionerDefaultImpl(
      new ProvisionerHookDefaultImpl
      , new ProvisionerIntrospectorDefaultImpl
      , loggerHook
      , new SetStrategyDefaultImpl
      , new ProxyStrategyFailingImpl
      , new FactoryStrategyDefaultImpl
      , new TraitStrategyDefaultImpl
      , new ProviderStrategyDefaultImpl(loggerHook)
      , new ClassStrategyDefaultImpl
      , new ImportStrategyDefaultImpl
      , new CustomStrategyDefaultImpl
      , new InstanceStrategyDefaultImpl
    )
  }

  final lazy val defaultBootstrapContextDefinition: ContextDefinition = TrivialDIDef
    .bind[CustomOpHandler].as(CustomOpHandler.NullCustomOpHander)
    .bind[LookupInterceptor].as(NullLookupInterceptor)
    .bind[ReflectionProvider.Runtime].as[ReflectionProviderDefaultImpl.Runtime]
    .bind[SymbolIntrospector.Runtime].as[SymbolIntrospectorDefaultImpl.Runtime]
    .bind[DependencyKeyProvider.Runtime].as[DependencyKeyProviderDefaultImpl.Runtime]
    .bind[PlanningHook].as[PlanningHookDefaultImpl]
    .bind[PlanningObserver].as[PlanningObserverDefaultImpl]
    .bind[PlanResolver].as[PlanResolverDefaultImpl]
    .bind[PlanAnalyzer].as[PlanAnalyzerDefaultImpl]
    .bind[PlanMergingPolicy].as[PlanMergingPolicyDefaultImpl]
    .bind[TheFactoryOfAllTheFactories].as[TheFactoryOfAllTheFactoriesDefaultImpl]
    .bind[ForwardingRefResolver].as[ForwardingRefResolverDefaultImpl]
    .bind[SanityChecker].as[SanityCheckerDefaultImpl]
    .bind[Planner].as[PlannerDefaultImpl]
    .bind[ProvisionerHook].as[ProvisionerHookDefaultImpl]
    .bind[ProvisionerIntrospector].as[ProvisionerIntrospectorDefaultImpl]
    .bind[LoggerHook].as[LoggerHookDefaultImpl]
    .bind[SetStrategy].as[SetStrategyDefaultImpl]
    .bind[ProxyStrategy].as[ProxyStrategyDefaultImpl]
    .bind[FactoryStrategy].as[FactoryStrategyDefaultImpl]
    .bind[TraitStrategy].as[TraitStrategyDefaultImpl]
    .bind[ProviderStrategy].as[ProviderStrategyDefaultImpl]
    .bind[ClassStrategy].as[ClassStrategyDefaultImpl]
    .bind[ImportStrategy].as[ImportStrategyDefaultImpl]
    .bind[CustomStrategy].as[CustomStrategyDefaultImpl]
    .bind[InstanceStrategy].as[InstanceStrategyDefaultImpl]
    .bind[Provisioner].as[ProvisionerDefaultImpl]
}
