package com.github.pshirshov.izumi.distage.bootstrap

import java.util.concurrent.atomic.AtomicReference

import com.github.pshirshov.izumi.distage.commons.{TraitInitTool, UnboxingTool}
import com.github.pshirshov.izumi.distage.model._
import com.github.pshirshov.izumi.distage.model.definition.{BootstrapContextModule, BootstrapContextModuleDef}
import com.github.pshirshov.izumi.distage.model.exceptions.{MissingInstanceException, SanityCheckFailedException}
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.planning._
import com.github.pshirshov.izumi.distage.model.provisioning.PlanInterpreter.FinalizersFilter
import com.github.pshirshov.izumi.distage.model.provisioning.strategies._
import com.github.pshirshov.izumi.distage.model.provisioning.{PlanInterpreter, _}
import com.github.pshirshov.izumi.distage.model.references.IdentifiedRef
import com.github.pshirshov.izumi.distage.model.reflection.universe.{MirrorProvider, RuntimeDIUniverse}
import com.github.pshirshov.izumi.distage.model.reflection.{DependencyKeyProvider, ReflectionProvider, SymbolIntrospector}
import com.github.pshirshov.izumi.distage.planning._
import com.github.pshirshov.izumi.distage.planning.gc.{NoopDIGC, TracingDIGC}
import com.github.pshirshov.izumi.distage.provisioning._
import com.github.pshirshov.izumi.distage.provisioning.strategies._
import com.github.pshirshov.izumi.distage.reflection._
import com.github.pshirshov.izumi.distage.{provisioning, _}
import com.github.pshirshov.izumi.fundamentals.platform.console.TrivialLogger
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import distage.TagK

final class BootstrapLocator(bindings: BootstrapContextModule) extends AbstractLocator {

  import BootstrapLocator.{bootstrapPlanner, bootstrapProducer}

  val parent: Option[AbstractLocator] = None

  val plan: OrderedPlan = bootstrapPlanner.plan(PlannerInput.noGc(bindings))

  private val bootstrappedContext: Locator = {
    val resource = bootstrapProducer.instantiate[Identity](plan, this, FinalizersFilter.all)
    resource.extract(resource.acquire).throwOnFailure()
  }

  private val _instances = new AtomicReference[Seq[IdentifiedRef]](bootstrappedContext.instances)

  override lazy val index: Map[RuntimeDIUniverse.DIKey, Any] = super.index

  override def instances: Seq[IdentifiedRef] = {
    Option(_instances.get()) match {
      case Some(value) =>
        value
      case None =>
        throw new SanityCheckFailedException(s"Injector bootstrap tried to enumerate instances from root locator, something is terribly wrong")
    }
  }

  override protected[distage] def finalizers[F[_] : TagK]: Seq[PlanInterpreter.Finalizer[F]] = Seq.empty

  override protected def unsafeLookup(key: RuntimeDIUniverse.DIKey): Option[Any] = {
    Option(_instances.get()) match {
      case Some(_) =>
        index.get(key)
      case None =>
        throw new MissingInstanceException(s"Injector bootstrap tried to perform a lookup from root locator, bootstrap plan in incomplete! Missing key: $key", key)
    }
  }
}

object BootstrapLocator {
  final val symbolIntrospector = new SymbolIntrospectorDefaultImpl.Runtime

  final val reflectionProvider = new ReflectionProviderDefaultImpl.Runtime(
    new DependencyKeyProviderDefaultImpl.Runtime(symbolIntrospector)
    , symbolIntrospector
  )

  final val mirrorProvider: MirrorProvider.Impl.type = MirrorProvider.Impl

  final val bootstrapPlanner: Planner = {
    val analyzer = new PlanAnalyzerDefaultImpl

    val bootstrapObserver = new PlanningObserverAggregate(Set(
      new BootstrapPlanningObserver(TrivialLogger.make[BootstrapLocator]("izumi.distage.debug.bootstrap")),
      //new GraphObserver(analyzer, Set.empty),
    ))

    val hook = new PlanningHookAggregate(Set.empty)
    val translator = new BindingTranslatorImpl(reflectionProvider, hook)
    new PlannerDefaultImpl(
      new ForwardingRefResolverDefaultImpl(analyzer, reflectionProvider, true),
      new SanityCheckerDefaultImpl(analyzer),
      NoopDIGC,
      bootstrapObserver,
      new PlanMergingPolicyDefaultImpl,
      hook,
      translator,
      analyzer,
      symbolIntrospector,
    )
  }

  final val bootstrapProducer: PlanInterpreter = {
    val loggerHook = new LoggerHookDefaultImpl // TODO: add user-controllable logs
    val unboxingTool = new UnboxingTool(mirrorProvider)
    val verifier = new provisioning.ProvisionOperationVerifier.Default(mirrorProvider, unboxingTool)
    new PlanInterpreterDefaultRuntimeImpl(
      new SetStrategyDefaultImpl(verifier)

      , new ProxyStrategyFailingImpl
      , new FactoryStrategyFailingImpl
      , new TraitStrategyFailingImpl

      , new FactoryProviderStrategyDefaultImpl(loggerHook)
      , new ProviderStrategyDefaultImpl
      , new ClassStrategyDefaultImpl(symbolIntrospector, mirrorProvider, unboxingTool)
      , new ImportStrategyDefaultImpl
      , new InstanceStrategyDefaultImpl
      , new EffectStrategyDefaultImpl
      , new ResourceStrategyDefaultImpl

      , new ProvisioningFailureInterceptorDefaultImpl
      , verifier
    )
  }

  final val noProxies: BootstrapContextModule = new BootstrapContextModuleDef {
    make[ProxyProvider].from[ProxyProviderFailingImpl]
  }

  final val defaultBootstrap: BootstrapContextModule = new BootstrapContextModuleDef {
    make[ReflectionProvider.Runtime].from[ReflectionProviderDefaultImpl.Runtime]
    make[SymbolIntrospector.Runtime].from[SymbolIntrospectorDefaultImpl.Runtime]
    make[DependencyKeyProvider.Runtime].from[DependencyKeyProviderDefaultImpl.Runtime]

    make[LoggerHook].from[LoggerHookDefaultImpl]
    make[MirrorProvider].from[MirrorProvider.Impl.type]

    make[UnboxingTool]
    make[TraitInitTool]
    make[ProvisionOperationVerifier].from[ProvisionOperationVerifier.Default]

    make[DIGarbageCollector].from[TracingDIGC.type]

    make[PlanAnalyzer].from[PlanAnalyzerDefaultImpl]
    make[PlanMergingPolicy].from[PlanMergingPolicyDefaultImpl]
    make[Boolean].named("distage.init-proxies-asap").fromValue(true)
    make[ForwardingRefResolver].from[ForwardingRefResolverDefaultImpl]
    make[SanityChecker].from[SanityCheckerDefaultImpl]
    make[Planner].from[PlannerDefaultImpl]
    make[SetStrategy].from[SetStrategyDefaultImpl]
    make[ProviderStrategy].from[ProviderStrategyDefaultImpl]
    make[FactoryProviderStrategy].from[FactoryProviderStrategyDefaultImpl]
    make[ClassStrategy].from[ClassStrategyDefaultImpl]
    make[ImportStrategy].from[ImportStrategyDefaultImpl]
    make[InstanceStrategy].from[InstanceStrategyDefaultImpl]
    make[EffectStrategy].from[EffectStrategyDefaultImpl]
    make[ResourceStrategy].from[ResourceStrategyDefaultImpl]
    make[PlanInterpreter].from[PlanInterpreterDefaultRuntimeImpl]
    make[ProvisioningFailureInterceptor].from[ProvisioningFailureInterceptorDefaultImpl]

    many[PlanningObserver]
    many[PlanningHook]
    make[PlanningHook].from[PlanningHookAggregate]
    make[PlanningObserver].from[PlanningObserverAggregate]

    make[BindingTranslator].from[BindingTranslatorImpl]

    make[ProxyStrategy].from[ProxyStrategyDefaultImpl]
    make[FactoryStrategy].from[FactoryStrategyDefaultImpl]
    make[TraitStrategy].from[TraitStrategyDefaultImpl]
  }

  final val noProxiesBootstrap: BootstrapContextModule = defaultBootstrap ++ noProxies

  final val noReflectionBootstrap: BootstrapContextModule = noProxiesBootstrap overridenBy new BootstrapContextModuleDef {
    make[ClassStrategy].from[ClassStrategyFailingImpl]
    make[ProxyStrategy].from[ProxyStrategyFailingImpl]
    make[FactoryStrategy].from[FactoryStrategyFailingImpl]
    make[TraitStrategy].from[TraitStrategyFailingImpl]
  }
}

