package izumi.distage.model

import izumi.distage.model.definition.DIResource.DIResourceBase
import izumi.distage.model.definition.{Activation, ModuleBase}
import izumi.distage.model.effect.DIEffect
import izumi.distage.model.plan.Roots
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.DIKey
import izumi.reflect.{Tag, TagK}
import izumi.fundamentals.platform.functional.Identity

/**
  * Injector can create an object graph ([[Locator]]) from a [[ModuleBase]] or an [[izumi.distage.model.plan.OrderedPlan]]
  *
  * @see [[Planner]]
  * @see [[Producer]]
  * */
trait Injector extends Planner with Producer {

  /**
    * Create an an object graph described by the `input` module,
    * designate all arguments of the provided function as roots of the graph and run the function,
    * deallocating the object graph when the function exits.
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
    * `Injector().produceRunF[F, A](moduleDef)(fn)` is a short-hand for:
    *
    * {{{
    *   Injector()
    *     .produceF[F](moduleDef, GCMode(fn.get.diKeys.toSet))
    *     .use(_.run(fn))
    * }}}
    * */
  final def produceRunF[F[_]: TagK: DIEffect, A](bindings: ModuleBase, activation: Activation = Activation.empty)(function: ProviderMagnet[F[A]]): F[A] = {
    produceF[F](plan(PlannerInput(bindings, activation, function.get.diKeys.toSet))).use(_.run(function))
  }

  /**
    * Create an effectful [[izumi.distage.model.definition.DIResource]] value that encapsulates the
    * allocation and cleanup of an object graph described by the `input` module,
    * designate all arguments of the provided function as roots of the graph and run the function.
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
    *       world => world.hello()
    *     }
    * }}}
    *
    * This is useful for the common case when you want to run an effect using the produced objects from the object graph,
    * without finalizing the object graph yet
    *
    * `Injector().produceEvalF[F, A](moduleDef)(fn)` is a short-hand for:
    *
    * {{{
    *   Injector()
    *     .produceF[F](moduleDef, GCMode(fn.get.diKeys.toSet))
    *     .evalMap(_.run(fn))
    * }}}
    * */
  final def produceEvalF[F[_]: TagK: DIEffect, A](
    bindings: ModuleBase,
    activation: Activation = Activation.empty,
  )(function: ProviderMagnet[F[A]]
  ): DIResourceBase[F, A] = {
    produceF[F](plan(PlannerInput(bindings, activation, function.get.diKeys.toSet))).evalMap(_.run(function))
  }

  /**
    * Create an effectful [[izumi.distage.model.definition.DIResource]] value that encapsulates the
    * allocation and cleanup of an object graph described by the `input` module,
    * designate `A` as the root of the graph and retrieve `A` from the result.
    *
    * {{{
    *   class HelloWorld { def hello() = println("hello world") }
    *
    *   Injector()
    *     .produceGet[HelloWorld](new ModuleDef {
    *       make[HelloWorld]
    *     })(_.hello())
    * }}}
    *
    * This is useful for the common case when your main logic class
    * is the root of your graph AND the object you want to use immediately.
    *
    * `Injector().produceGetF[F, A](moduleDef)` is a short-hand for:
    *
    * {{{
    *   Injector()
    *     .produceF[F](moduleDef, GCMode(DIKey.get[A]))
    *     .map(_.get[A])
    * }}}
    * */
  final def produceGetF[F[_]: TagK: DIEffect, A: Tag](bindings: ModuleBase, activation: Activation): DIResourceBase[F, A] = {
    produceF[F](plan(PlannerInput(bindings, activation, DIKey.get[A]))).map(_.get[A])
  }
  final def produceGetF[F[_]: TagK: DIEffect, A: Tag](name: String, activation: Activation)(bindings: ModuleBase): DIResourceBase[F, A] = {
    produceF[F](plan(PlannerInput(bindings, activation, DIKey.get[A].named(name)))).map(_.get[A](name))
  }

  /**
    * Create an effectful [[izumi.distage.model.definition.DIResource]] value that encapsulates the
    * allocation and cleanup of an object graph described by `input`
    *
    *
    * {{{
    *   class HelloWorld { def hello() = println("hello world") }
    *
    *   Injector()
    *     .produceGet[HelloWorld](new ModuleDef {
    *       make[HelloWorld]
    *     })(_.hello())
    * }}}
    *
    * @param input Bindings created by [[izumi.distage.model.definition.ModuleDef]] DSL
    *              and garbage collection roots.
    *
    *              Garbage collector will remove all bindings that aren't direct or indirect dependencies
    *              of the chosen root DIKeys from the plan - they will never be instantiated.
    *
    *              If left empty, garbage collection will not be performed – that would be equivalent to
    *              designating all DIKeys as roots.
    * @return A Resource value that encapsulates allocation and cleanup of the object graph described by `input`
    */
  final def produceF[F[_]: TagK: DIEffect](input: PlannerInput): DIResourceBase[F, Locator] = {
    produceF[F](plan(input))
  }
  final def produceF[F[_]: TagK: DIEffect](bindings: ModuleBase, roots: Roots, activation: Activation = Activation.empty): DIResourceBase[F, Locator] = {
    produceF[F](plan(PlannerInput(bindings, activation, roots)))
  }

  final def produceRun[A: Tag](bindings: ModuleBase, activation: Activation = Activation.empty)(function: ProviderMagnet[A]): A =
    produceRunF[Identity, A](bindings, activation)(function)
  final def produceEval[A: Tag](bindings: ModuleBase, activation: Activation = Activation.empty)(function: ProviderMagnet[A]): DIResourceBase[Identity, A] =
    produceEvalF[Identity, A](bindings, activation)(function)

  final def produceGet[A: Tag](bindings: ModuleBase, activation: Activation = Activation.empty): DIResourceBase[Identity, A] =
    produceGetF[Identity, A](bindings, activation)
  final def produceGet[A: Tag](name: String)(bindings: ModuleBase, activation: Activation): DIResourceBase[Identity, A] =
    produceGetF[Identity, A](name, activation)(bindings)

  final def produce(input: PlannerInput): DIResourceBase[Identity, Locator] = produceF[Identity](input)
  final def produce(bindings: ModuleBase, roots: Roots, activation: Activation = Activation.empty): DIResourceBase[Identity, Locator] =
    produceF[Identity](bindings, roots, activation)
}
