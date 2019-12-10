package izumi.distage.bootstrap

import java.util.concurrent.atomic.AtomicReference

import distage.TagK
import izumi.distage._
import izumi.distage.model._
import izumi.distage.model.definition.{Activation, BootstrapContextModule, BootstrapContextModuleDef}
import izumi.distage.model.exceptions.{MissingInstanceException, SanityCheckFailedException}
import izumi.distage.model.plan._
import izumi.distage.model.planning._
import izumi.distage.model.provisioning.PlanInterpreter.FinalizerFilter
import izumi.distage.model.provisioning.proxies.ProxyProvider
import izumi.distage.model.provisioning.proxies.ProxyProvider.ProxyProviderFailingImpl
import izumi.distage.model.provisioning.strategies._
import izumi.distage.model.provisioning.{PlanInterpreter, ProvisioningFailureInterceptor}
import izumi.distage.model.references.IdentifiedRef
import izumi.distage.model.reflection.MirrorProvider
import izumi.distage.model.reflection.universe.RuntimeDIUniverse
import izumi.distage.planning._
import izumi.distage.planning.gc.{NoopDIGC, TracingDIGC}
import izumi.distage.provisioning._
import izumi.distage.provisioning.strategies._
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.functional.Identity

final class BootstrapLocator(bindings: BootstrapContextModule) extends AbstractLocator {
  override val parent: Option[AbstractLocator] = None
  override val plan: OrderedPlan = BootstrapLocator.bootstrapPlanner.plan(PlannerInput.noGc(bindings))
  override lazy val index: Map[RuntimeDIUniverse.DIKey, Any] = super.index

  private[this] val bootstrappedContext: Locator = {
    val resource = BootstrapLocator.bootstrapProducer.instantiate[Identity](plan, this, FinalizerFilter.all)
    resource.extract(resource.acquire).throwOnFailure()
  }

  private[this] val _instances = new AtomicReference[collection.Seq[IdentifiedRef]](bootstrappedContext.instances)

  override def instances: collection.Seq[IdentifiedRef] = {
    Option(_instances.get()) match {
      case Some(value) =>
        value
      case None =>
        throw new SanityCheckFailedException(s"Injector bootstrap tried to enumerate instances from root locator, something is terribly wrong")
    }
  }

  override def finalizers[F[_]: TagK]: collection.Seq[PlanInterpreter.Finalizer[F]] = Nil

  override protected def lookupLocalUnsafe(key: RuntimeDIUniverse.DIKey): Option[Any] = {
    Option(_instances.get()) match {
      case Some(_) =>
        index.get(key)
      case None =>
        throw new MissingInstanceException(s"Injector bootstrap tried to perform a lookup from root locator, bootstrap plan in incomplete! Missing key: $key", key)
    }
  }
}

object BootstrapLocator {
  @inline private[this] final val mirrorProvider: MirrorProvider.Impl.type = MirrorProvider.Impl

  private final val bootstrapPlanner: Planner = {
    val analyzer = new PlanAnalyzerDefaultImpl

    val bootstrapObserver = new PlanningObserverAggregate(Set(
      new BootstrapPlanningObserver(TrivialLogger.make[BootstrapLocator]("izumi.distage.debug.bootstrap")),
      //new GraphObserver(analyzer, Set.empty),
    ))

    val hook = new PlanningHookAggregate(Set.empty)
    val translator = new BindingTranslator.Impl(hook)
    new PlannerDefaultImpl(
      forwardingRefResolver = new ForwardingRefResolverDefaultImpl(analyzer, true),
      sanityChecker = new SanityCheckerDefaultImpl(analyzer),
      gc = NoopDIGC,
      planningObserver = bootstrapObserver,
      planMergingPolicy = new PruningPlanMergingPolicyDefaultImpl(Activation.empty),
      hook = hook,
      bindingTranslator = translator,
      analyzer = analyzer,
      mirrorProvider = mirrorProvider
    )
  }

  private final val bootstrapProducer: PlanInterpreter = {
    val verifier = new ProvisionOperationVerifier.Default(mirrorProvider)
    new PlanInterpreterDefaultRuntimeImpl(
      setStrategy = new SetStrategyDefaultImpl,
      proxyStrategy = new ProxyStrategyFailingImpl,
      providerStrategy = new ProviderStrategyDefaultImpl,
      importStrategy = new ImportStrategyDefaultImpl,
      instanceStrategy = new InstanceStrategyDefaultImpl,
      effectStrategy = new EffectStrategyDefaultImpl,
      resourceStrategy = new ResourceStrategyDefaultImpl,
      failureHandler = new ProvisioningFailureInterceptor.DefaultImpl,
      verifier = verifier,
    )
  }

  final lazy val noProxies: BootstrapContextModule = new BootstrapContextModuleDef {
    make[ProxyProvider].from[ProxyProviderFailingImpl]
  }

  final lazy val defaultBootstrap: BootstrapContextModule = new BootstrapContextModuleDef {
    make[Boolean].named("distage.init-proxies-asap").fromValue(true)
    make[Activation].fromValue(Activation.empty)

    make[ProvisionOperationVerifier].from[ProvisionOperationVerifier.Default]

    make[MirrorProvider].fromValue(mirrorProvider)
    make[DIGarbageCollector].from[TracingDIGC.type]

    make[PlanAnalyzer].from[PlanAnalyzerDefaultImpl]
    make[PlanMergingPolicy].from[PruningPlanMergingPolicyDefaultImpl]
    make[ForwardingRefResolver].from[ForwardingRefResolverDefaultImpl]
    make[SanityChecker].from[SanityCheckerDefaultImpl]
    make[Planner].from[PlannerDefaultImpl]
    make[SetStrategy].from[SetStrategyDefaultImpl]
    make[ProviderStrategy].from[ProviderStrategyDefaultImpl]
    make[ImportStrategy].from[ImportStrategyDefaultImpl]
    make[InstanceStrategy].from[InstanceStrategyDefaultImpl]
    make[EffectStrategy].from[EffectStrategyDefaultImpl]
    make[ResourceStrategy].from[ResourceStrategyDefaultImpl]
    make[PlanInterpreter].from[PlanInterpreterDefaultRuntimeImpl]
    make[ProvisioningFailureInterceptor].from[ProvisioningFailureInterceptor.DefaultImpl]

    many[PlanningObserver]
    many[PlanningHook]

    make[PlanningObserver].from[PlanningObserverAggregate]
    make[PlanningHook].from[PlanningHookAggregate]

    make[BindingTranslator].from[BindingTranslator.Impl]

//    make[ProxyStrategy].from[ProxyStrategyDefaultImpl]
    make[ProxyStrategy].from[ProxyStrategyFailingImpl]
  }

  /** Disable cglib proxies, but allow by-name parameters to resolve cycles */
  final lazy val noProxiesBootstrap: BootstrapContextModule = defaultBootstrap ++ noProxies

  /** Disable all cycle resolution, immediately throw when circular dependencies are found, whether by-name or not */
  final lazy val noCyclesBootstrap: BootstrapContextModule = noProxiesBootstrap overridenBy new BootstrapContextModuleDef {
    make[ProxyStrategy].from[ProxyStrategyFailingImpl]
  }
}

