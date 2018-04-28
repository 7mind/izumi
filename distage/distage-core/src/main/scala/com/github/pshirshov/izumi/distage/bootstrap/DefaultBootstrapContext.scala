package com.github.pshirshov.izumi.distage.bootstrap

import com.github.pshirshov.izumi.distage._
import com.github.pshirshov.izumi.distage.model._
import com.github.pshirshov.izumi.distage.model.definition._
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.planning._
import com.github.pshirshov.izumi.distage.model.provisioning._
import com.github.pshirshov.izumi.distage.model.provisioning.strategies._
import com.github.pshirshov.izumi.distage.model.references.IdentifiedRef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse
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
    val reflectionProvider = new ReflectionProviderDefaultImpl.Java(
      new DependencyKeyProviderDefaultImpl.Java
      , new SymbolIntrospectorDefaultImpl.Java
    )

    val bootstrapObserver = new BootstrapPlanningObserver(TrivialLogger.make[DefaultBootstrapContext]("izumi.debug.distage.bootstrap"))

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
    .instance[CustomOpHandler](CustomOpHandler.NullCustomOpHander)
    .instance[LookupInterceptor](NullLookupInterceptor)
    .binding[ReflectionProvider.Java, ReflectionProviderDefaultImpl.Java]
    .binding[SymbolIntrospector.Java, SymbolIntrospectorDefaultImpl.Java]
    .binding[DependencyKeyProvider.Java, DependencyKeyProviderDefaultImpl.Java]
    .binding[PlanningHook, PlanningHookDefaultImpl]
    .binding[PlanningObserver, PlanningObserverDefaultImpl]
    .binding[PlanResolver, PlanResolverDefaultImpl]
    .binding[PlanAnalyzer, PlanAnalyzerDefaultImpl]
    .binding[PlanMergingPolicy, PlanMergingPolicyDefaultImpl]
    .binding[TheFactoryOfAllTheFactories, TheFactoryOfAllTheFactoriesDefaultImpl]
    .binding[ForwardingRefResolver, ForwardingRefResolverDefaultImpl]
    .binding[SanityChecker, SanityCheckerDefaultImpl]
    .binding[Planner, PlannerDefaultImpl]
    .binding[ProvisionerHook, ProvisionerHookDefaultImpl]
    .binding[ProvisionerIntrospector, ProvisionerIntrospectorDefaultImpl]
    .binding[LoggerHook, LoggerHookDefaultImpl]
    .binding[SetStrategy, SetStrategyDefaultImpl]
    .binding[ProxyStrategy, ProxyStrategyDefaultImpl]
    .binding[FactoryStrategy, FactoryStrategyDefaultImpl]
    .binding[TraitStrategy, TraitStrategyDefaultImpl]
    .binding[ProviderStrategy, ProviderStrategyDefaultImpl]
    .binding[ClassStrategy, ClassStrategyDefaultImpl]
    .binding[ImportStrategy, ImportStrategyDefaultImpl]
    .binding[CustomStrategy, CustomStrategyDefaultImpl]
    .binding[InstanceStrategy, InstanceStrategyDefaultImpl]
    .binding[Provisioner, ProvisionerDefaultImpl]
}
