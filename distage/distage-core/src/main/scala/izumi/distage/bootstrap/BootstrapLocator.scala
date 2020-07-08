package izumi.distage.bootstrap

import java.util.concurrent.atomic.AtomicReference

import izumi.distage.AbstractLocator
import izumi.distage.bootstrap.CglibBootstrap.CglibProxyProvider
import izumi.distage.model._
import izumi.distage.model.definition._
import izumi.distage.model.exceptions.{MissingInstanceException, SanityCheckFailedException}
import izumi.distage.model.plan._
import izumi.distage.model.planning._
import izumi.distage.model.provisioning.PlanInterpreter.FinalizerFilter
import izumi.distage.model.provisioning.proxies.ProxyProvider
import izumi.distage.model.provisioning.proxies.ProxyProvider.ProxyProviderFailingImpl
import izumi.distage.model.provisioning.strategies._
import izumi.distage.model.provisioning.{PlanInterpreter, ProvisioningFailureInterceptor}
import izumi.distage.model.references.IdentifiedRef
import izumi.distage.model.reflection.{DIKey, MirrorProvider}
import izumi.distage.planning._
import izumi.distage.planning.gc.{NoopDIGC, TracingDIGC}
import izumi.distage.provisioning._
import izumi.distage.provisioning.strategies._
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.functional.Identity
import izumi.reflect.TagK

final class BootstrapLocator(bindings0: BootstrapContextModule, bootstrapActivation: Activation) extends AbstractLocator {
  override val parent: Option[AbstractLocator] = None
  override val plan: OrderedPlan = {
    // BootstrapModule & bootstrap plugins cannot modify `Activation` after 0.11.0,
    // it's solely under control of `PlannerInput` now,
    // please open an issue if you need the ability to override Activation using BootstrapModule
    val bindings = bindings0 ++ new BootstrapModuleDef {
        make[Activation].fromValue(bootstrapActivation)
        make[BootstrapModule].fromValue(bindings0)
      }

    BootstrapLocator
      .bootstrapPlanner
      .plan(PlannerInput.noGC(bindings, bootstrapActivation))
  }
  override lazy val index: Map[DIKey, Any] = super.index

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

  override protected def lookupLocalUnsafe(key: DIKey): Option[Any] = {
    Option(_instances.get()) match {
      case Some(_) =>
        index.get(key)
      case None =>
        throw new MissingInstanceException(s"Injector bootstrap tried to perform a lookup from root locator, bootstrap plan in incomplete! Missing key: $key", key)
    }
  }

  override def meta: LocatorMeta = LocatorMeta.empty
}

object BootstrapLocator {
  @inline private[this] final val mirrorProvider: MirrorProvider.Impl.type = MirrorProvider.Impl

  private final val bootstrapPlanner: Planner = {
    val analyzer = new PlanAnalyzerDefaultImpl

    val bootstrapObserver = new PlanningObserverAggregate(
      Set(
        new BootstrapPlanningObserver(TrivialLogger.make[BootstrapLocator](DebugProperties.`izumi.distage.debug.bootstrap`))
        //new GraphObserver(analyzer, Set.empty),
      )
    )

    val hook = new PlanningHookAggregate(Set.empty)
    val translator = new BindingTranslator.Impl()
    val forwardingRefResolver = new ForwardingRefResolverDefaultImpl(analyzer, true)
    val sanityChecker = new SanityCheckerDefaultImpl(analyzer)
    val gc = NoopDIGC
    val mp = mirrorProvider

    new PlannerDefaultImpl(
      forwardingRefResolver = forwardingRefResolver,
      sanityChecker = sanityChecker,
      gc = gc,
      planningObserver = bootstrapObserver,
      hook = hook,
      bindingTranslator = translator,
      analyzer = analyzer,
      mirrorProvider = mp,
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

  final val defaultBootstrap: BootstrapContextModule = new BootstrapContextModuleDef {
    make[Boolean].named("distage.init-proxies-asap").fromValue(true)

    make[ProvisionOperationVerifier].from[ProvisionOperationVerifier.Default]

    make[MirrorProvider].fromValue(mirrorProvider)
    make[DIGarbageCollector].from[TracingDIGC.type]

    make[PlanAnalyzer].from[PlanAnalyzerDefaultImpl]

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

    make[ProxyProvider].tagged(Cycles.Proxy).from[CglibProxyProvider]
    make[ProxyStrategy].tagged(Cycles.Proxy).from[ProxyStrategyDefaultImpl]

    make[ProxyProvider].tagged(Cycles.Byname).from[ProxyProviderFailingImpl]
    make[ProxyStrategy].tagged(Cycles.Byname).from[ProxyStrategyDefaultImpl]

    make[ProxyProvider].tagged(Cycles.Disable).from[ProxyProviderFailingImpl]
    make[ProxyStrategy].tagged(Cycles.Disable).from[ProxyStrategyFailingImpl]
  }

  final val defaultBootstrapActivation: Activation = Activation(Cycles -> Cycles.Proxy)
}
