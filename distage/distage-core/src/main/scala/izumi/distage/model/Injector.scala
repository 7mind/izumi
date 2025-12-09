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
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.collections.nonempty.NESet
import izumi.fundamentals.platform.functional.Identity
import izumi.reflect.{Tag, TagK}

/**
  * Injector creates object graphs ([[izumi.distage.model.Locator]]s) from a [[izumi.distage.model.definition.ModuleDef]] or from an [[izumi.distage.model.plan.Plan]]
  *
  * @see [[izumi.distage.model.Planner]]
  * @see [[izumi.distage.model.Producer]]
  */
trait Injector[F[_]] extends Planner with Producer {
  /**
    * Create an object graph described by the `input` module,
    * designate all arguments of the provided function as roots of the graph,
    * and run the function, deallocating the object graph when the function exits.
    *
    * {{{
    *   class Hello { def hello() = println("hello") }
    *   class World { def world() = println("world") }
    *
    *   Injector()
    *     .produceRun(new ModuleDef {
    *       make[Hello]
    *       make[World]
    *     }) {
    *       (hello: Hello, world: World) =>
    *         hello.hello()
    *         world.world()
    *     }
    * }}}
    *
    * This is useful for the common case when you want to run an effect using the produced objects from the object graph
    * and deallocate the object graph once the effect is finished
    *
    * `Injector[F]().produceRun[A](moduleDef)(fn)` is a short-hand for:
    *
    * {{{
    *   Injector[F]()
    *     .produce(moduleDef, Roots(fn.get.diKeys.toSet))
    *     .use(_.run(fn)): F[A]
    * }}}
    *
    * @param bindings   Bindings created by [[izumi.distage.model.definition.ModuleDef]] DSL
    * @param activation A map of axes of configuration to choices along these axes
    * @param function   N-ary [[izumi.distage.model.providers.Functoid]] function for which arguments will be designated as roots and provided from the object graph
    */
  final def produceRun[A](
    bindings: ModuleBase,
    activation: Activation = Activation.empty,
  )(function: Functoid[F[A]]
  ): F[A] = {
    produce(PlannerInput(bindings, function.get.diKeys.toSet, activation))
      .use(_.run(function))
  }

  /**
    * Create an effectful [[izumi.distage.model.definition.Lifecycle]] value that encapsulates the
    * allocation and cleanup of an object graph described by the `input` module,
    * designate all arguments of the provided function as roots of the graph
    * and run the function.
    *
    * {{{
    *   class Hello { def hello() = println("hello") }
    *   class World { def world() = println("world") }
    *
    *   Injector()
    *     .produceEval(new ModuleDef {
    *       make[Hello]
    *       make[World]
    *     }) {
    *       (hello: Hello, world: World) =>
    *         hello.hello()
    *         world
    *     }
    *     .use {
    *       world =>
    *         world.world()
    *     }
    * }}}
    *
    * This is useful for the common case when you want to run an effect using the produced objects from the object graph,
    * without finalizing the object graph yet
    *
    * `Injector[F]().produceEval[A](moduleDef)(fn)` is a short-hand for:
    *
    * {{{
    *   Injector[F]()
    *     .produce(moduleDef, Roots(fn.get.diKeys.toSet))
    *     .evalMap(_.run(fn)): Lifecycle[F, A]
    * }}}
    *
    * @param bindings   Bindings created by [[izumi.distage.model.definition.ModuleDef]] DSL
    * @param activation A map of axes of configuration to choices along these axes
    * @param function   N-ary [[izumi.distage.model.providers.Functoid]] function for which arguments will be designated as roots and provided from the object graph
    */
  final def produceEval[A](
    bindings: ModuleBase,
    activation: Activation = Activation.empty,
  )(function: Functoid[F[A]]
  ): Lifecycle[F, A] = {
    produce(PlannerInput(bindings, function.get.diKeys.toSet, activation))
      .evalMap(_.run(function))
  }

  /**
    * Create an effectful [[izumi.distage.model.definition.Lifecycle]] value that encapsulates the
    * allocation and cleanup of an object graph described by the `input` module,
    * designate `A` as the root of the graph and retrieve `A` from the result.
    *
    * {{{
    *   class HelloWorld {
    *     def hello() = println("hello world")
    *   }
    *
    *   Injector()
    *     .produceGet[HelloWorld](new ModuleDef {
    *       make[HelloWorld]
    *     })
    *     .use(_.hello())
    * }}}
    *
    * This is useful for the common case when your main logic class
    * is the root of your graph AND the object you want to use immediately.
    *
    * `Injector[F]().produceGet[A](moduleDef)` is a short-hand for:
    *
    * {{{
    *   Injector[F]()
    *     .produce(moduleDef, Roots(DIKey.get[A]))
    *     .map(_.get[A]): Lifecycle[F, A]
    * }}}
    *
    * @param bindings   Bindings created by [[izumi.distage.model.definition.ModuleDef]] DSL
    * @param activation A map of axes of configuration to choices along these axes
    */
  final def produceGet[A: Tag](bindings: ModuleBase, activation: Activation): Lifecycle[F, A] = {
    produce(PlannerInput(bindings, activation, DIKey.get[A]))
      .map(_.get[A])
  }
  final def produceGet[A: Tag](bindings: ModuleBase): Lifecycle[F, A] = {
    produceGet[A](bindings, Activation.empty)
  }
  final def produceGet[A: Tag](name: Identifier)(bindings: ModuleBase, activation: Activation = Activation.empty): Lifecycle[F, A] = {
    produce(PlannerInput(bindings, activation, DIKey.get[A].named(name)))
      .map(_.get[A](name))
  }

  /**
    * Create an effectful [[izumi.distage.model.definition.Lifecycle]] value that encapsulates the
    * allocation and cleanup of an object graph described by `input`
    *
    * {{{
    *   class HelloWorld {
    *     def hello() = println("hello world")
    *   }
    *
    *   Injector()
    *     .produce(PlannerInput(
    *       bindings = new ModuleDef {
    *         make[HelloWorld]
    *       },
    *       activation = Activation.empty,
    *       roots = Roots.target[HelloWorld],
    *     ))
    *     .use(_.get[HelloWorld].hello())
    * }}}
    *
    * @param input Bindings created by [[izumi.distage.model.definition.ModuleDef]] DSL
    *              and garbage collection roots.
    *
    *              Garbage collector will remove all bindings that aren't direct or indirect dependencies
    *              of the chosen `root` DIKeys from the plan - they will never be instantiated.
    *
    *              If left empty, garbage collection will not be performed – that would be equivalent to
    *              designating all DIKeys as roots.
    * @return A Resource value that encapsulates allocation and cleanup of the object graph described by `input`
    */
  final def produce(input: PlannerInput): Lifecycle[F, Locator] = {
    produceCustomF[F](input)
  }
  final def produce(
    bindings: ModuleBase,
    roots: Roots,
    activation: Activation = Activation.empty,
    locatorPrivacy: LocatorPrivacy = LocatorPrivacy.PublicByDefault,
  ): Lifecycle[F, Locator] = {
    produce(PlannerInput(bindings, roots, activation, locatorPrivacy))
  }

  /**
    * Create an effectful [[izumi.distage.model.definition.Lifecycle]] value that encapsulates the
    * allocation and cleanup of an object graph described by an existing `plan`
    *
    * {{{
    *   class HelloWorld {
    *     def hello() = println("hello world")
    *   }
    *
    *   val injector = Injector()
    *
    *   val plan = injector.plan(PlannerInput(
    *       bindings = new ModuleDef {
    *         make[HelloWorld]
    *       },
    *       activation = Activation.empty,
    *       roots = Roots.target[HelloWorld],
    *     )).getOrThrow()
    *
    *   injector
    *     .produce(plan)
    *     .use(_.get[HelloWorld].hello())
    * }}}
    *
    * @param plan Computed wiring plan, may be produced by calling the [[plan]] method
    * @return A Resource value that encapsulates allocation and cleanup of the object graph described by `input`
    */
  final def produce(plan: Plan): Lifecycle[F, Locator] = {
    produceCustomF[F](plan)
  }

  /** Produce [[izumi.distage.model.Locator]] interpreting effect and resource bindings into the provided effect type */
  final def produceCustomF[G[_]: TagK](input: PlannerInput)(implicit G: QuasiIO[G]): Lifecycle[G, Locator] = {
    Lifecycle
      .liftF(G.maybeSuspendEither(plan(input).aggregateErrors))
      .flatMap(produceCustomF[G])
  }
  final def produceDetailedCustomF[G[_]: TagK](input: PlannerInput)(implicit G: QuasiIO[G]): Lifecycle[G, Either[FailedProvision, Locator]] = {
    Lifecycle
      .liftF(G.maybeSuspendEither(plan(input).aggregateErrors))
      .flatMap(produceDetailedCustomF[G])
  }

  /** Produce [[izumi.distage.model.Locator]], supporting only effect and resource bindings in `Identity` */
  final def produceCustomIdentity(input: PlannerInput): Lifecycle[Identity, Locator] = {
    produceCustomF[Identity](input)
  }
  final def produceDetailedIdentity(input: PlannerInput): Lifecycle[Identity, Either[FailedProvision, Locator]] = {
    produceDetailedCustomF[Identity](input)
  }

  /**
    * Efficiently check all possible paths for the given module to the given `roots`,
    *
    * This is a "raw" version of [[izumi.distage.framework.PlanCheck]] API, please use `PlanCheck` for all non-exotic needs.
    *
    * This method executes at runtime, to check correctness at compile-time use `PlanCheck` API from `distage-framework` module.
    *
    * @see [[https://izumi.7mind.io/distage/distage-framework.html#compile-time-checks Compile-Time Checks]]
    *
    * @return Unit
    * @throws PlanCheckException on found issues
    */
  final def assert(
    bindings: ModuleBase,
    roots: Roots,
    excludedActivations: Set[NESet[AxisChoice]] = Set.empty,
  ): Unit = {
    PlanVerifier()
      .verify[F](
        bindings = bindings,
        roots = roots,
        providedKeys = providedKeys,
        excludedActivations = excludedActivations.map(_.map(_.toAxisPoint)),
      ).throwOnError()
  }

  /**
    * Efficiently check all possible paths for the given module to the given `roots`,
    *
    * This is a "raw" version of [[izumi.distage.framework.PlanCheck]] API, please use `PlanCheck` for all non-exotic needs.
    *
    * This method executes at runtime, to check correctness at compile-time use `PlanCheck` API from `distage-framework` module.
    *
    * @see [[https://izumi.7mind.io/distage/distage-framework.html#compile-time-checks Compile-Time Checks]]
    *
    * @return Set of issues if any.
    * @throws Nothing Does not throw.
    */
  final def verify(
    bindings: ModuleBase,
    roots: Roots,
    excludedActivations: Set[NESet[AxisChoice]] = Set.empty,
  ): PlanVerifierResult = {
    PlanVerifier()
      .verify[F](
        bindings = bindings,
        roots = roots,
        providedKeys = providedKeys,
        excludedActivations = excludedActivations.map(_.map(_.toAxisPoint)),
      )
  }

  /** Keys that will be available to the module interpreted by this Injector, includes parent Locator keys, [[izumi.distage.modules.DefaultModule]] & Injector's self-reference keys */
  def providedKeys: Set[DIKey]
  def providedEnvironment: InjectorProvidedEnv

  protected implicit def tagK: TagK[F]
  protected implicit def F: QuasiIO[F]
}

object Injector extends InjectorFactory {

  /**
    * Create a new Injector
    *
    * @tparam F                   The effect type to use for effect and resource bindings and the result of [[izumi.distage.model.Injector#produce]]
    *
    * @param bootstrapBase        Initial bootstrap context module, such as [[izumi.distage.bootstrap.BootstrapLocator.defaultBootstrap]]
    *
    * @param bootstrapActivation  A map of axes of configuration to choices along these axes.
    *                             The passed activation will affect _only_ the bootstrapping of the `Injector` itself (see [[izumi.distage.bootstrap.BootstrapLocator]]).
    *                             To set activation choices for subsequent injections, pass `Activation` to the methods of the created `Injector`
    *
    * @param parent               If set, this locator will be used as parent for the bootstrap locator.
    *                             Use this parameter if you want to reuse components from another injection BUT also want to
    *                             recreate the bootstrap environment with new parameters. If you just want to reuse all components,
    *                             including the bootstrap environment, use [[inherit]]
    *
    * @param bootstrapOverrides   Optional: Overrides of Injector's own bootstrap environment - injector itself is constructed with DI.
    *                             They can be used to customize the Injector, e.g. by adding members to [[izumi.distage.model.planning.PlanningHook]] Set.
    */
  override def apply[F[_]: QuasiIO: TagK: DefaultModule](
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
    * Create a new default Injector with [[izumi.fundamentals.platform.functional.Identity]] effect type
    *
    * Use `apply[F]()` variant to specify a different effect type
    *
    * @note this method exists only because of Scala 2.12's sub-par implicit handling:
    *       2.12 fails to default to `QuasiIO.quasiIOIdentity` when writing `Injector()` if cats-effect
    *       is on the classpath because of recursive (on 2.12: diverging) instances in `cats.effect.kernel.Sync` object
    */
  override def apply(): Injector[Identity] = apply[Identity]()

  /**
    * Create a new injector inheriting configuration, hooks and the object graph from a previous injection.
    *
    * @tparam F the effect type to use for effect and resource bindings and the result of [[izumi.distage.model.Injector#produce]]
    *
    * @param parent Instances from parent [[izumi.distage.model.Locator]] will be available as imports in new Injector's [[izumi.distage.model.Producer#produce produce]]
    */
  override def inherit[F[_]: QuasiIO: TagK](parent: Locator): Injector[F] = {
    new InjectorDefaultImpl(this, parent, definition.Module.empty)
  }

  /**
    * Create a new injector inheriting configuration, hooks and the object graph from a previous injection.
    *
    * Unlike [[inherit]] this will fully (re)create the `defaultModule` in subsequent injections,
    * without reusing the existing instances in `parent`.
    *
    * @tparam F the effect type to use for effect and resource bindings and the result of [[izumi.distage.model.Injector#produce]]
    *
    * @param parent Instances from parent [[izumi.distage.model.Locator]] will be available as imports in new Injector's [[izumi.distage.model.Producer#produce produce]]
    */
  override def inheritWithNewDefaultModule[F[_]: QuasiIO: TagK](parent: Locator, defaultModule: DefaultModule[F]): Injector[F] = {
    inheritWithNewDefaultModuleImpl(this, parent, defaultModule)
  }

  override def providedKeys[F[_]: DefaultModule](bootstrapOverrides: BootstrapModule*): Set[DIKey] = {
    providedKeys[F](defaultBootstrap, bootstrapOverrides*)
  }

  override def providedKeys[F[_]: DefaultModule](bootstrapBase: BootstrapContextModule, bootstrapOverrides: BootstrapModule*): Set[DIKey] = {
    (bootstrapBase.keysIterator ++
    bootstrapOverrides.iterator.flatMap(_.keysIterator) ++
    BootstrapLocator.selfReflectionKeys.iterator ++
    IdentitySupportModule.keysIterator ++
    DefaultModule[F].keysIterator ++
    InjectorDefaultImpl.providedKeys.iterator).toSet
  }

  override def bootloader[F[_]](
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

    override final def apply[F[_]: QuasiIO: TagK: DefaultModule](
      parent: Option[Locator],
      bootstrapBase: BootstrapContextModule,
      bootstrapActivation: Activation,
      bootstrapOverrides: Seq[BootstrapModule],
      locatorPrivacy: LocatorPrivacy,
      bootstrapRootsMode: BootstrapRootsMode,
    ): Injector[F] = {
      bootstrap(this, bootstrapBase, defaultBootstrapActivation ++ bootstrapActivation, parent, bootstrapOverrides, locatorPrivacy, bootstrapRootsMode)
    }

    override final def apply(): Injector[Identity] = apply[Identity]()

    override final def inherit[F[_]: QuasiIO: TagK](parent: Locator): Injector[F] = {
      new InjectorDefaultImpl(this, parent, definition.Module.empty)
    }

    override final def inheritWithNewDefaultModule[F[_]: QuasiIO: TagK](parent: Locator, defaultModule: DefaultModule[F]): Injector[F] = {
      inheritWithNewDefaultModuleImpl(this, parent, defaultModule)
    }

    override def providedKeys[F[_]: DefaultModule](bootstrapOverrides: BootstrapModule*): Set[DIKey] = {
      Injector.providedKeys[F](bootstrapOverrides*)
    }

    override def providedKeys[F[_]: DefaultModule](bootstrapBase: BootstrapContextModule, bootstrapOverrides: BootstrapModule*): Set[DIKey] = {
      Injector.providedKeys[F](bootstrapBase, bootstrapOverrides*)
    }

    override protected final def defaultBootstrap: BootstrapContextModule = BootstrapLocator.defaultBootstrap
    override protected final def defaultBootstrapActivation: Activation = definition.Activation(Cycles -> cycleChoice)
    override protected def defaultBootstrapLocatorPrivacy: LocatorPrivacy = BootstrapLocator.defaultBoostrapPrivacy
    @inline override protected def defaultBootstrapRootsMode: BootstrapRootsMode = BootstrapRootsMode.UseGC
  }

  private def bootstrap[F[_]: QuasiIO: TagK: DefaultModule](
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

  private def inheritWithNewDefaultModuleImpl[F[_]: QuasiIO: TagK](
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
}
