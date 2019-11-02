package izumi.distage.bootstrap

import java.util.concurrent.atomic.AtomicReference

import izumi.distage.commons.{TraitInitTool, UnboxingTool}
import izumi.distage.model._
import izumi.distage.model.definition.{BootstrapContextModule, BootstrapContextModuleDef}
import izumi.distage.model.exceptions.{MissingInstanceException, SanityCheckFailedException}
import izumi.distage.model.plan._
import izumi.distage.model.planning._
import izumi.distage.model.provisioning.PlanInterpreter.FinalizersFilter
import izumi.distage.model.provisioning.strategies._
import izumi.distage.model.provisioning.{PlanInterpreter, _}
import izumi.distage.model.references.IdentifiedRef
import izumi.distage.model.reflection.universe.{MirrorProvider, RuntimeDIUniverse}
import izumi.distage.model.reflection.{DependencyKeyProvider, ReflectionProvider, SymbolIntrospector}
import izumi.distage.planning._
import izumi.distage.planning.gc.{NoopDIGC, TracingDIGC}
import izumi.distage.provisioning._
import izumi.distage.provisioning.strategies._
import izumi.distage.reflection._
import izumi.distage.{provisioning, _}
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.functional.Identity
import distage.TagK

final class BootstrapLocator(bindings: BootstrapContextModule) extends AbstractLocator {

  import BootstrapLocator.{bootstrapPlanner, bootstrapProducer}

  val parent: Option[AbstractLocator] = None

  val plan: OrderedPlan = bootstrapPlanner.plan(PlannerInput.noGc(bindings))

  private val bootstrappedContext: Locator = {
    val resource = bootstrapProducer.instantiate[Identity](plan, this, FinalizersFilter.all)
    resource.extract(resource.acquire).throwOnFailure()
  }

  private val _instances = new AtomicReference[collection.Seq[IdentifiedRef]](bootstrappedContext.instances)

  override lazy val index: Map[RuntimeDIUniverse.DIKey, Any] = super.index

  override def instances: collection.Seq[IdentifiedRef] = {
    Option(_instances.get()) match {
      case Some(value) =>
        value
      case None =>
        throw new SanityCheckFailedException(s"Injector bootstrap tried to enumerate instances from root locator, something is terribly wrong")
    }
  }

  override protected[distage] def finalizers[F[_] : TagK]: collection.Seq[PlanInterpreter.Finalizer[F]] = collection.Seq.empty

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
  final val symbolIntrospector: SymbolIntrospectorDefaultImpl.Runtime = new SymbolIntrospectorDefaultImpl.Runtime

  final val reflectionProvider: ReflectionProviderDefaultImpl.Runtime = new ReflectionProviderDefaultImpl.Runtime(
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
    make[Boolean].named("distage.init-proxies-asap").from(true)
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

