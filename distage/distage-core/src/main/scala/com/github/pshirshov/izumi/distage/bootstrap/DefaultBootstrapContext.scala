package com.github.pshirshov.izumi.distage.bootstrap

import com.github.pshirshov.izumi.distage._
import com.github.pshirshov.izumi.distage.model._
import com.github.pshirshov.izumi.distage.model.definition._
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.planning._
import com.github.pshirshov.izumi.distage.model.provisioning._
import com.github.pshirshov.izumi.distage.model.provisioning.strategies._
import com.github.pshirshov.izumi.distage.model.references.IdentifiedRef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.{DependencyKeyProvider, ReflectionProvider, SymbolIntrospector}
import com.github.pshirshov.izumi.distage.planning._
import com.github.pshirshov.izumi.distage.provisioning._
import com.github.pshirshov.izumi.distage.provisioning.strategies._
import com.github.pshirshov.izumi.distage.reflection._
import com.github.pshirshov.izumi.fundamentals.platform.console.TrivialLogger


class DefaultBootstrapContext(contextDefinition: ModuleDef) extends AbstractLocator {

  import DefaultBootstrapContext._

  lazy val parent: Option[AbstractLocator] = None

  lazy val plan: FinalPlan = bootstrapPlanner.plan(contextDefinition)

  def enumerate: Stream[IdentifiedRef] = bootstrappedContext.enumerate

  protected lazy val bootstrappedContext: ProvisionImmutable = bootstrapProducer.provision(plan, this)

  protected def unsafeLookup(key: RuntimeDIUniverse.DIKey): Option[Any] = bootstrappedContext.get(key)

}

object DefaultBootstrapContext {
  protected lazy val bootstrapPlanner: Planner = {
    val symbolIntrospector = new SymbolIntrospectorDefaultImpl.Runtime

    val reflectionProvider = new ReflectionProviderDefaultImpl.Runtime(
      new DependencyKeyProviderDefaultImpl.Runtime(symbolIntrospector)
      , symbolIntrospector
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
      , Set(new PlanningHookDefaultImpl)
    )
  }


  protected lazy val bootstrapProducer: Provisioner = {
    val loggerHook = new LoggerHookDefaultImpl // TODO: add user-controllable logs

    new ProvisionerDefaultImpl(
      new SetStrategyDefaultImpl
      , new ProxyStrategyFailingImpl
      , new FactoryStrategyDefaultImpl
      , new TraitStrategyDefaultImpl
      , new ProviderStrategyDefaultImpl(loggerHook)
      , new ClassStrategyDefaultImpl
      , new ImportStrategyDefaultImpl
      , new CustomStrategyDefaultImpl
      , new InstanceStrategyDefaultImpl
//      , new ProvisionerHookDefaultImpl
//      , new ProvisionerIntrospectorDefaultImpl
//      , loggerHook
    )
  }

  final lazy val defaultBootstrapContextDefinition: ModuleDef = TrivialModuleDef
    .bind[CustomOpHandler].as(CustomOpHandler.NullCustomOpHander)
    .bind[LookupInterceptor].as(NullLookupInterceptor)
    .bind[ReflectionProvider.Runtime].as[ReflectionProviderDefaultImpl.Runtime]
    .bind[SymbolIntrospector.Runtime].as[SymbolIntrospectorDefaultImpl.Runtime]
    .bind[DependencyKeyProvider.Runtime].as[DependencyKeyProviderDefaultImpl.Runtime]
    .bind[PlanningHook].as[PlanningHookDefaultImpl]
    .bind[PlanningObserver].as[PlanningObserverDefaultImpl]
    //.bind[PlanningObserver](new BootstrapPlanningObserver(new TrivialLoggerImpl(SystemOutStringSink)))
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
    .set[PlanningHook]
      .element[PlanningHookDefaultImpl]
}
