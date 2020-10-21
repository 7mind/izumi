package izumi.distage.model

import izumi.distage.model.definition.{Activation, Identifier, Lifecycle, ModuleBase}
import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.plan.{OrderedPlan, Roots}
import izumi.distage.model.providers.Functoid
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, FinalizerFilter}
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.platform.functional.Identity
import izumi.reflect.{Tag, TagK}

/**
  * Injector can create an object graph ([[Locator]]) from a [[izumi.distage.model.definition.ModuleDef]] or an [[izumi.distage.model.plan.OrderedPlan]]
  *
  * @see [[izumi.distage.model.Planner]]
  * @see [[izumi.distage.model.Producer]]
  */
trait Injector[F[_]] extends Planner with Producer {

  /**
    * Create an an object graph described by the `input` module,
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
    * @param function   N-ary [[Functoid]] function for which arguments will be designated as roots and provided from the object graph
    */
  final def produceRun[A](
    bindings: ModuleBase,
    activation: Activation = Activation.empty,
  )(function: Functoid[F[A]]
  ): F[A] = {
    produceCustomF(plan(PlannerInput(bindings, activation, function.get.diKeys.toSet))).use(_.run(function))
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
    * @param function   N-ary [[Functoid]] function for which arguments will be designated as roots and provided from the object graph
    */
  final def produceEval[A](
    bindings: ModuleBase,
    activation: Activation = Activation.empty,
  )(function: Functoid[F[A]]
  ): Lifecycle[F, A] = {
    produceCustomF(plan(PlannerInput(bindings, activation, function.get.diKeys.toSet))).evalMap(_.run(function))
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
    produceCustomF(plan(PlannerInput(bindings, activation, DIKey.get[A]))).map(_.get[A])
  }
  final def produceGet[A: Tag](bindings: ModuleBase): Lifecycle[F, A] = {
    produceGet[A](bindings, Activation.empty)
  }
  final def produceGet[A: Tag](name: Identifier)(bindings: ModuleBase, activation: Activation = Activation.empty): Lifecycle[F, A] = {
    produceCustomF(plan(PlannerInput(bindings, activation, DIKey.get[A].named(name)))).map(_.get[A](name))
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
    produceCustomF[F](plan(input))
  }
  final def produce(bindings: ModuleBase, roots: Roots, activation: Activation = Activation.empty): Lifecycle[F, Locator] = {
    produceCustomF[F](plan(PlannerInput(bindings, activation, roots)))
  }

  final def produce(plan: OrderedPlan): Lifecycle[F, Locator] = {
    produceCustomF[F](plan)
  }

  /** Produce [[Locator]] interpreting effect- and resource-bindings into the provided `F` */
  final def produceCustomF[G[_]: TagK: QuasiIO](plannerInput: PlannerInput): Lifecycle[G, Locator] = {
    produceCustomF[G](plan(plannerInput))
  }
  final def produceDetailedCustomF[G[_]: TagK: QuasiIO](plannerInput: PlannerInput): Lifecycle[G, Either[FailedProvision[G], Locator]] = {
    produceDetailedCustomF[G](plan(plannerInput))
  }

  /** Produce [[Locator]], supporting only effect- and resource-bindings in `Identity` */
  final def produceCustomIdentity(plannerInput: PlannerInput): Lifecycle[Identity, Locator] = {
    produceCustomF[Identity](plan(plannerInput))
  }
  final def produceDetailedIdentity(plannerInput: PlannerInput): Lifecycle[Identity, Either[FailedProvision[Identity], Locator]] = {
    produceDetailedCustomF[Identity](plan(plannerInput))
  }

  @deprecated("Use .produceRun. Parameterize Injector with `F` on creation: `Injector[F]()`", "1.0")
  final def produceRunF[A: Tag](bindings: ModuleBase, activation: Activation = Activation.empty)(function: Functoid[F[A]]): F[A] =
    produceRun[A](bindings, activation)(function)
  @deprecated("Use .produceEval. Parameterize Injector with `F` on creation: `Injector[F]()`", "1.0")
  final def produceEvalF[A: Tag](bindings: ModuleBase, activation: Activation = Activation.empty)(function: Functoid[F[A]]): Lifecycle[F, A] =
    produceEval[A](bindings, activation)(function)

  @deprecated("Use .produceGet. Parameterize Injector with `F` on creation: `Injector[F]()`", "1.0")
  final def produceGetF[A: Tag](bindings: ModuleBase, activation: Activation): Lifecycle[F, A] =
    produceGet[A](bindings, activation)
  @deprecated("Use .produceGet. Parameterize Injector with `F` on creation: `Injector[F]()`", "1.0")
  final def produceGetF[A: Tag](bindings: ModuleBase): Lifecycle[F, A] =
    produceGet[A](bindings)
  @deprecated("Use .produceGet. Parameterize Injector with `F` on creation: `Injector[F]()`", "1.0")
  final def produceGetF[A: Tag](name: Identifier)(bindings: ModuleBase, activation: Activation = Activation.empty): Lifecycle[F, A] =
    produceGet[A](name)(bindings, activation)

  @deprecated("Use .produce. Parameterize Injector with `F` on creation: `Injector[F]()`", "1.0")
  final def produceF(input: PlannerInput): Lifecycle[F, Locator] =
    produce(input)
  @deprecated("Use .produce. Parameterize Injector with `F` on creation: `Injector[F]()`", "1.0")
  final def produceF(bindings: ModuleBase, roots: Roots, activation: Activation = Activation.empty): Lifecycle[F, Locator] =
    produce(bindings, roots, activation)

  @deprecated("Use .produce. Parameterize Injector with `F` on creation: `Injector[F]()`", "1.0")
  final def produceF(plan: OrderedPlan): Lifecycle[F, Locator] = {
    produceCustomF[F](plan)
  }

  protected[this] implicit def tagK: TagK[F]
  protected[this] implicit def F: QuasiIO[F]
}