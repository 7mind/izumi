package izumi.distage.model

import izumi.distage.bootstrap.{BootstrapLocator, BootstrapRootsMode, Cycles}
import izumi.distage.model.definition.Axis.AxisChoice
import izumi.distage.model.definition.{Activation, BootstrapContextModule, BootstrapModule, Identifier, Lifecycle, LocatorPrivacy, ModuleBase}
import izumi.distage.model.plan.{Plan, Roots}
import izumi.distage.model.providers.Functoid
import izumi.distage.model.provisioning.PlanInterpreter.FailedProvision
import izumi.distage.model.recursive.Bootloader
import izumi.distage.model.reflection.DIKey
import izumi.distage.modules.DefaultModule
import izumi.distage.modules.support.IdentitySupportModule
import izumi.distage.planning.solver.PlanVerifier
import izumi.distage.planning.solver.PlanVerifier.PlanVerifierResult
import izumi.distage.{InjectorDefaultImpl, InjectorFactory}
import izumi.functional.bio.{Bifunctorized, IO2, Primitives2}
import izumi.fundamentals.collections.nonempty.NESet
import izumi.reflect.{Tag, TagKK}

/**
  * Injector creates object graphs ([[izumi.distage.model.Locator]]s) from a [[izumi.distage.model.definition.ModuleDef]] or from an [[izumi.distage.model.plan.Plan]]
  *
  * @see [[izumi.distage.model.Planner]]
  * @see [[izumi.distage.model.Producer]]
  */
trait Injector[F[+_, +_]] extends Planner with Producer {
  /**
    * Create an object graph described by the `input` module,
    * designate all arguments of the provided function as roots of the graph,
    * and run the function, deallocating the object graph when the function exits.
    *
    * `Injector[F]().produceRun[A](moduleDef)(fn)` is a short-hand for:
    *
    * {{{
    *   Injector[F]()
    *     .produce(moduleDef, Roots(fn.get.diKeys.toSet))
    *     .use(_.run(fn)): F[Throwable, A]
    * }}}
    *
    * @param bindings   Bindings created by [[izumi.distage.model.definition.ModuleDef]] DSL
    * @param activation A map of axes of configuration to choices along these axes
    * @param function   N-ary [[izumi.distage.model.providers.Functoid]] function for which arguments will be designated as roots and provided from the object graph
    */
  final def produceRun[A](
    bindings: ModuleBase,
    activation: Activation = Activation.empty,
  )(function: Functoid[F[Throwable, A]]
  ): F[Throwable, A] = {
    produce(PlannerInput(bindings, function.get.diKeys.toSet, activation))
      .use(_.run(function))
  }

  /**
    * Create an effectful [[izumi.distage.model.definition.Lifecycle]] value that encapsulates the
    * allocation and cleanup of an object graph described by the `input` module,
    * designate all arguments of the provided function as roots of the graph
    * and run the function.
    *
    * `Injector[F]().produceEval[A](moduleDef)(fn)` is a short-hand for:
    *
    * {{{
    *   Injector[F]()
    *     .produce(moduleDef, Roots(fn.get.diKeys.toSet))
    *     .evalMap(_.run(fn)): Lifecycle[F, Throwable, A]
    * }}}
    *
    * @param bindings   Bindings created by [[izumi.distage.model.definition.ModuleDef]] DSL
    * @param activation A map of axes of configuration to choices along these axes
    * @param function   N-ary [[izumi.distage.model.providers.Functoid]] function for which arguments will be designated as roots and provided from the object graph
    */
  final def produceEval[A](
    bindings: ModuleBase,
    activation: Activation = Activation.empty,
  )(function: Functoid[F[Throwable, A]]
  ): Lifecycle[F, Throwable, A] = {
    produce(PlannerInput(bindings, function.get.diKeys.toSet, activation))
      .evalMap(_.run(function))
  }

  /**
    * Create an effectful [[izumi.distage.model.definition.Lifecycle]] value that encapsulates the
    * allocation and cleanup of an object graph described by the `input` module,
    * designate `A` as the root of the graph and retrieve `A` from the result.
    */
  final def produceGet[A: Tag](bindings: ModuleBase, activation: Activation): Lifecycle[F, Throwable, A] = {
    produce(PlannerInput(bindings, activation, DIKey.get[A]))
      .map(_.get[A])
  }
  final def produceGet[A: Tag](bindings: ModuleBase): Lifecycle[F, Throwable, A] = {
    produceGet[A](bindings, Activation.empty)
  }
  final def produceGet[A: Tag](name: Identifier)(bindings: ModuleBase, activation: Activation = Activation.empty): Lifecycle[F, Throwable, A] = {
    produce(PlannerInput(bindings, activation, DIKey.get[A].named(name)))
      .map(_.get[A](name))
  }

  /**
    * Create an effectful [[izumi.distage.model.definition.Lifecycle]] value that encapsulates the
    * allocation and cleanup of an object graph described by `input`
    */
  final def produce(input: PlannerInput): Lifecycle[F, Throwable, Locator] = {
    produceCustomF[F](input)
  }
  final def produce(
    bindings: ModuleBase,
    roots: Roots,
    activation: Activation = Activation.empty,
    locatorPrivacy: LocatorPrivacy = LocatorPrivacy.PublicByDefault,
  ): Lifecycle[F, Throwable, Locator] = {
    produce(PlannerInput(bindings, roots, activation, locatorPrivacy))
  }

  /**
    * Create an effectful [[izumi.distage.model.definition.Lifecycle]] value that encapsulates the
    * allocation and cleanup of an object graph described by an existing `plan`
    */
  final def produce(plan: Plan): Lifecycle[F, Throwable, Locator] = {
    produceCustomF[F](plan)
  }

  /** Produce [[izumi.distage.model.Locator]] interpreting effect and resource bindings into the provided effect type */
  final def produceCustomF[G[+_, +_]: TagKK](input: PlannerInput)(implicit G: IO2[G], P: Primitives2[G]): Lifecycle[G, Throwable, Locator] = {
    Lifecycle
      .liftF[G, Throwable, Plan](G.fromEither(plan(input).aggregateErrors))
      .flatMap((p: Plan) => produceCustomF[G](p))
  }
  final def produceDetailedCustomF[G[+_, +_]: TagKK](input: PlannerInput)(implicit G: IO2[G], P: Primitives2[G]): Lifecycle[G, Throwable, Either[FailedProvision, Locator]] = {
    Lifecycle
      .liftF[G, Throwable, Plan](G.fromEither(plan(input).aggregateErrors))
      .flatMap((p: Plan) => produceDetailedCustomF[G](p))
  }

  /** Keys that will be available to the module interpreted by this Injector, includes parent Locator keys, [[izumi.distage.modules.DefaultModule]] & Injector's self-reference keys */
  def providedKeys: Set[DIKey]
  def providedEnvironment: InjectorProvidedEnv

  protected implicit def tagK: TagKK[F]
  protected implicit def F: IO2[F]
  protected implicit def P: Primitives2[F]

  /**
    * Efficiently check all possible paths for the given module to the given `roots`,
    *
    * @return Unit
    * @throws PlanCheckException on found issues
    */
  final def assert(
    bindings: ModuleBase,
    roots: Roots,
    excludedActivations: Set[NESet[AxisChoice]] = Set.empty,
  )(implicit tagThrowableF: izumi.reflect.TagK[F[Throwable, _]]
  ): Unit = {
    Injector
      .verifyImpl[F](this, bindings, roots, excludedActivations)(using tagK, tagThrowableF)
      .throwOnError()
  }

  /**
    * Efficiently check all possible paths for the given module to the given `roots`.
    *
    * @return Set of issues if any.
    * @throws Nothing Does not throw.
    */
  final def verify(
    bindings: ModuleBase,
    roots: Roots,
    excludedActivations: Set[NESet[AxisChoice]] = Set.empty,
  )(implicit tagThrowableF: izumi.reflect.TagK[F[Throwable, _]]
  ): PlanVerifierResult = {
    Injector
      .verifyImpl[F](this, bindings, roots, excludedActivations)(using tagK, tagThrowableF)
  }
}

object Injector extends InjectorFactory {

  /**
    * Create a new Injector
    *
    * @tparam F                   The bifunctor effect type to use for effect and resource bindings
    */
  override def apply[F[+_, +_]: IO2: Primitives2: TagKK: DefaultModule](
    parent: Option[Locator] = None,
    bootstrapBase: BootstrapContextModule = defaultBootstrap,
    bootstrapActivation: Activation = defaultBootstrapActivation,
    bootstrapOverrides: Seq[BootstrapModule] = Nil,
    bootstrapLocatorPrivacy: LocatorPrivacy = defaultBootstrapLocatorPrivacy,
    bootstrapRootsMode: BootstrapRootsMode = defaultBootstrapRootsMode,
  ): Injector[F] = {
    bootstrap(this, bootstrapBase, defaultBootstrapActivation ++ bootstrapActivation, parent, bootstrapOverrides, bootstrapLocatorPrivacy, bootstrapRootsMode)
  }

  /**
    * Create a new default Injector with [[izumi.functional.bio.Bifunctorized.IdentityBifunctorized]] effect type.
    * (lawful MiniBIO-backed carrier for plain synchronous Scala).
    */
  override def apply(): Injector[Bifunctorized.IdentityBifunctorized] = apply[Bifunctorized.IdentityBifunctorized]()

  /**
    * Create a new injector inheriting configuration, hooks and the object graph from a previous injection.
    */
  override def inherit[F[+_, +_]: IO2: Primitives2: TagKK](parent: Locator): Injector[F] = {
    new InjectorDefaultImpl(this, parent, definition.Module.empty)
  }

  /**
    * Create a new injector inheriting configuration, hooks and the object graph from a previous injection.
    */
  override def inheritWithNewDefaultModule[F[+_, +_]: IO2: Primitives2: TagKK](parent: Locator, defaultModule: DefaultModule[F]): Injector[F] = {
    inheritWithNewDefaultModuleImpl(this, parent, defaultModule)
  }

  override def providedKeys[F[+_, +_]: DefaultModule](bootstrapOverrides: BootstrapModule*): Set[DIKey] = {
    providedKeys[F](defaultBootstrap, bootstrapOverrides*)
  }

  override def providedKeys[F[+_, +_]: DefaultModule](bootstrapBase: BootstrapContextModule, bootstrapOverrides: BootstrapModule*): Set[DIKey] = {
    (bootstrapBase.keysIterator ++
    bootstrapOverrides.iterator.flatMap(_.keysIterator) ++
    BootstrapLocator.selfReflectionKeys.iterator ++
    IdentitySupportModule.keysIterator ++
    DefaultModule[F].keysIterator ++
    InjectorDefaultImpl.providedKeys.iterator).toSet
  }

  override def bootloader[F[+_, +_]](
    bootstrapModule: BootstrapModule,
    bootstrapActivation: Activation,
    defaultModule: DefaultModule[F],
    input: PlannerInput,
  ): Bootloader = {
    super.bootloader(bootstrapModule, bootstrapActivation, defaultModule, input)
  }

  /** Enable bytebuddy proxies, but try to resolve cycles using by-name parameters if they can be used */
  def Standard: Injector.type = this

  /** Disable bytebuddy proxies, allow only by-name parameters to resolve cycles */
  object NoProxies extends InjectorBootstrap(Cycles.Byname)

  /** Disable all cycle resolution, immediately throw when circular dependencies are found, whether by-name or not */
  object NoCycles extends InjectorBootstrap(Cycles.Disable)

  private[Injector] sealed abstract class InjectorBootstrap(
    cycleChoice: Cycles.AxisChoiceDef
  ) extends InjectorFactory {

    override final def apply[F[+_, +_]: IO2: Primitives2: TagKK: DefaultModule](
      parent: Option[Locator],
      bootstrapBase: BootstrapContextModule,
      bootstrapActivation: Activation,
      bootstrapOverrides: Seq[BootstrapModule],
      locatorPrivacy: LocatorPrivacy,
      bootstrapRootsMode: BootstrapRootsMode,
    ): Injector[F] = {
      bootstrap(this, bootstrapBase, defaultBootstrapActivation ++ bootstrapActivation, parent, bootstrapOverrides, locatorPrivacy, bootstrapRootsMode)
    }

    override final def apply(): Injector[Bifunctorized.IdentityBifunctorized] = apply[Bifunctorized.IdentityBifunctorized]()

    override final def inherit[F[+_, +_]: IO2: Primitives2: TagKK](parent: Locator): Injector[F] = {
      new InjectorDefaultImpl(this, parent, definition.Module.empty)
    }

    override final def inheritWithNewDefaultModule[F[+_, +_]: IO2: Primitives2: TagKK](parent: Locator, defaultModule: DefaultModule[F]): Injector[F] = {
      inheritWithNewDefaultModuleImpl(this, parent, defaultModule)
    }

    override def providedKeys[F[+_, +_]: DefaultModule](bootstrapOverrides: BootstrapModule*): Set[DIKey] = {
      Injector.providedKeys[F](bootstrapOverrides*)
    }

    override def providedKeys[F[+_, +_]: DefaultModule](bootstrapBase: BootstrapContextModule, bootstrapOverrides: BootstrapModule*): Set[DIKey] = {
      Injector.providedKeys[F](bootstrapBase, bootstrapOverrides*)
    }

    override protected final def defaultBootstrap: BootstrapContextModule = BootstrapLocator.defaultBootstrap
    override protected final def defaultBootstrapActivation: Activation = definition.Activation(Cycles -> cycleChoice)
    override protected def defaultBootstrapLocatorPrivacy: LocatorPrivacy = BootstrapLocator.defaultBoostrapPrivacy
    @inline override protected def defaultBootstrapRootsMode: BootstrapRootsMode = BootstrapRootsMode.UseGC
  }

  private def bootstrap[F[+_, +_]: IO2: Primitives2: TagKK: DefaultModule](
    injectorFactory: InjectorFactory,
    bootstrapBase: BootstrapContextModule,
    activation: Activation,
    parent: Option[Locator],
    bootstrapOverrides: Seq[BootstrapModule],
    locatorPrivacy: LocatorPrivacy,
    bootstrapRootsMode: BootstrapRootsMode,
  ): Injector[F] = {
    val bootstrapLocator = BootstrapLocator.bootstrap(bootstrapBase, activation, bootstrapOverrides, parent, locatorPrivacy, bootstrapRootsMode)
    inheritWithNewDefaultModuleImpl(injectorFactory, bootstrapLocator, implicitly)
  }

  private def inheritWithNewDefaultModuleImpl[F[+_, +_]: IO2: Primitives2: TagKK](
    injectorFactory: InjectorFactory,
    parent: Locator,
    defaultModule: DefaultModule[F],
  ): Injector[F] = {
    val defaultModule0 = defaultModule.module ++ IdentitySupportModule // Identity support is always on
    new InjectorDefaultImpl(injectorFactory, parent, defaultModule = defaultModule0)
  }

  @inline override protected def defaultBootstrap: BootstrapContextModule = BootstrapLocator.defaultBootstrap
  @inline override protected def defaultBootstrapActivation: Activation = BootstrapLocator.defaultBootstrapActivation
  @inline override protected def defaultBootstrapLocatorPrivacy: LocatorPrivacy = BootstrapLocator.defaultBoostrapPrivacy
  @inline override protected def defaultBootstrapRootsMode: BootstrapRootsMode = BootstrapRootsMode.UseGC

  /** Helper that bridges `Injector.assert`/`verify` (bifunctor F) to `PlanVerifier.verify[F[Throwable, _]]`
    * (monofunctor unary form). Relies on izumi-reflect's macro to auto-derive `TagK[F[Throwable, _]]`
    * from the available `TagKK[F]` in implicit scope at the call site.
    */
  private[Injector] def verifyImpl[F[+_, +_]: TagKK](
    injector: Injector[F],
    bindings: ModuleBase,
    roots: Roots,
    excludedActivations: Set[NESet[AxisChoice]],
  )(implicit tkF: izumi.reflect.TagK[F[Throwable, _]]
  ): PlanVerifierResult = {
    PlanVerifier()
      .verify[F[Throwable, _]](
        bindings = bindings,
        roots = roots,
        providedKeys = injector.providedKeys,
        excludedActivations = excludedActivations.map(_.map(_.toAxisPoint)),
      )
  }
}
