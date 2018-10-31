package com.github.pshirshov.izumi.distage.bootstrap

import com.github.pshirshov.izumi.distage._
import com.github.pshirshov.izumi.distage.model._
import com.github.pshirshov.izumi.distage.model.definition.{BootstrapContextModule, BootstrapContextModuleDef}
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.planning._
import com.github.pshirshov.izumi.distage.model.provisioning._
import com.github.pshirshov.izumi.distage.model.provisioning.strategies._
import com.github.pshirshov.izumi.distage.model.references.IdentifiedRef
import com.github.pshirshov.izumi.distage.model.reflection.universe.{MirrorProvider, RuntimeDIUniverse}
import com.github.pshirshov.izumi.distage.model.reflection.{DependencyKeyProvider, ReflectionProvider, SymbolIntrospector}
import com.github.pshirshov.izumi.distage.planning._
import com.github.pshirshov.izumi.distage.provisioning._
import com.github.pshirshov.izumi.distage.provisioning.strategies._
import com.github.pshirshov.izumi.distage.reflection._
import com.github.pshirshov.izumi.fundamentals.platform.console.TrivialLogger


class DefaultBootstrapContext(contextDefinition: BootstrapContextModule) extends AbstractLocator {

  import DefaultBootstrapContext._

  val parent: Option[AbstractLocator] = None

  val plan: OrderedPlan = bootstrapPlanner.plan(contextDefinition)

  protected val bootstrappedContext: ProvisionImmutable = {
    bootstrapProducer.provision(plan, this)
  }

  def instances: Seq[IdentifiedRef] = {
    bootstrappedContext.enumerate
  }

  protected def unsafeLookup(key: RuntimeDIUniverse.DIKey): Option[Any] = {
    bootstrappedContext.get(key)
  }

}

object DefaultBootstrapContext {
  protected val symbolIntrospector = new SymbolIntrospectorDefaultImpl.Runtime

  protected val reflectionProvider = new ReflectionProviderDefaultImpl.Runtime(
    new DependencyKeyProviderDefaultImpl.Runtime(symbolIntrospector)
    , symbolIntrospector
  )

  protected val mirrorProvider: MirrorProvider.Impl.type = MirrorProvider.Impl

  protected lazy val bootstrapPlanner: Planner = {

    val bootstrapObserver = new BootstrapPlanningObserver(TrivialLogger.make[DefaultBootstrapContext]("izumi.distage.debug.bootstrap"))

    val analyzer = new PlanAnalyzerDefaultImpl

    new PlannerDefaultImpl(
      new ForwardingRefResolverDefaultImpl(analyzer, reflectionProvider)
      , reflectionProvider
      , new SanityCheckerDefaultImpl(analyzer)
      , bootstrapObserver
      , new PlanMergingPolicyDefaultImpl(analyzer, symbolIntrospector)
      , Set(new PlanningHookDefaultImpl)
    )
  }

  protected lazy val bootstrapProducer: Provisioner = {
    val loggerHook = new LoggerHookDefaultImpl // TODO: add user-controllable logs

    new ProvisionerDefaultImpl(
      new SetStrategyDefaultImpl(mirrorProvider)

      , new ProxyStrategyFailingImpl
      , new FactoryStrategyFailingImpl
      , new TraitStrategyFailingImpl

      , new FactoryProviderStrategyDefaultImpl(loggerHook)
      , new ProviderStrategyDefaultImpl
      , new ClassStrategyDefaultImpl(symbolIntrospector, mirrorProvider)
      , new ImportStrategyDefaultImpl
      , new InstanceStrategyDefaultImpl
      , new ProvisioningFailureInterceptorDefaultImpl
    )
  }

  final lazy val noProxies: BootstrapContextModule = new BootstrapContextModuleDef {
    make[ProxyProvider].from[ProxyProviderFailingImpl]
  }

  final lazy val defaultBootstrap: BootstrapContextModule = new BootstrapContextModuleDef {
    make[LookupInterceptor].from(NullLookupInterceptor)
    make[ReflectionProvider.Runtime].from[ReflectionProviderDefaultImpl.Runtime]
    make[SymbolIntrospector.Runtime].from[SymbolIntrospectorDefaultImpl.Runtime]
    make[DependencyKeyProvider.Runtime].from[DependencyKeyProviderDefaultImpl.Runtime]

    make[PlanningObserver].from[PlanningObserverDefaultImpl]
    make[LoggerHook].from[LoggerHookDefaultImpl]
    make[MirrorProvider].from[MirrorProvider.Impl.type]

    make[PlanAnalyzer].from[PlanAnalyzerDefaultImpl]
    make[PlanMergingPolicy].from[PlanMergingPolicyDefaultImpl]
    make[TheFactoryOfAllTheFactories].from[TheFactoryOfAllTheFactoriesDefaultImpl]
    make[ForwardingRefResolver].from[ForwardingRefResolverDefaultImpl]
    make[SanityChecker].from[SanityCheckerDefaultImpl]
    make[Planner].from[PlannerDefaultImpl]
    make[SetStrategy].from[SetStrategyDefaultImpl]
    make[ProviderStrategy].from[ProviderStrategyDefaultImpl]
    make[FactoryProviderStrategy].from[FactoryProviderStrategyDefaultImpl]
    make[ClassStrategy].from[ClassStrategyDefaultImpl]
    make[ImportStrategy].from[ImportStrategyDefaultImpl]
    make[InstanceStrategy].from[InstanceStrategyDefaultImpl]
    make[Provisioner].from[ProvisionerDefaultImpl]
    make[ProvisioningFailureInterceptor].from[ProvisioningFailureInterceptorDefaultImpl]
    many[PlanningHook]
      .add[PlanningHookDefaultImpl]

    make[ProxyStrategy].from[ProxyStrategyDefaultImpl]
    make[FactoryStrategy].from[FactoryStrategyDefaultImpl]
    make[TraitStrategy].from[TraitStrategyDefaultImpl]
  }

  final lazy val noProxiesBootstrap: BootstrapContextModule = defaultBootstrap ++ noProxies

  final lazy val noCogensBootstrap: BootstrapContextModule = noProxiesBootstrap overridenBy new BootstrapContextModuleDef {
    make[ClassStrategy].from[ClassStrategyFailingImpl]
    make[ProxyStrategy].from[ProxyStrategyFailingImpl]
    make[FactoryStrategy].from[FactoryStrategyFailingImpl]
    make[TraitStrategy].from[TraitStrategyFailingImpl]
  }
}
