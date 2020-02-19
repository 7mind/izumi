package izumi.distage.model

import izumi.distage.model.definition.DIResource.DIResourceBase
import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.effect.DIEffect
import izumi.distage.model.plan.GCMode
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey
import izumi.fundamentals.reflection.Tags.{Tag, TagK}
import izumi.fundamentals.platform.functional.Identity

/**
  * Injector can create an object graph ([[Locator]]) from a [[ModuleBase]] or an [[izumi.distage.model.plan.OrderedPlan]]
  *
  * @see [[Planner]]
  * @see [[Producer]]
  * */
trait Injector extends Planner with Producer {

  /**
    * Create an effectful [[izumi.distage.model.definition.DIResource]] value that encapsulates the
    * allocation and cleanup of an object graph described by `input`
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
  final def produceF[F[_]: TagK: DIEffect](bindings: ModuleBase, mode: GCMode): DIResourceBase[F, Locator] = {
    produceF[F](plan(PlannerInput(bindings, mode)))
  }

  /**
    * Create an object graph described by the `input` module,
    * designate `A` as the root of the graph and retrieve `A` from the result.
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
  final def produceGetF[F[_]: TagK: DIEffect, A: Tag](bindings: ModuleBase): DIResourceBase[F, A] = {
    produceF[F](plan(PlannerInput(bindings, DIKey.get[A]))).map(_.get[A])
  }
  final def produceGetF[F[_]: TagK: DIEffect, A: Tag](name: String)(bindings: ModuleBase): DIResourceBase[F, A] = {
    produceF[F](plan(PlannerInput(bindings, DIKey.get[A].named(name)))).map(_.get[A](name))
  }

  final def produce(input: PlannerInput): DIResourceBase[Identity, Locator] = produceF[Identity](input)
  final def produce(bindings: ModuleBase, mode: GCMode): DIResourceBase[Identity, Locator] = produceF[Identity](bindings, mode)

  final def produceGet[A: Tag](bindings: ModuleBase): DIResourceBase[Identity, A] = produceGetF[Identity, A](bindings)
  final def produceGet[A: Tag](name: String)(bindings: ModuleBase): DIResourceBase[Identity, A] = produceGetF[Identity, A](name)(bindings)
}
