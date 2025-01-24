package izumi.distage

import izumi.distage.model.definition.Identifier
import izumi.functional.lifecycle.Lifecycle
import izumi.distage.model.plan.Plan
import izumi.distage.model.providers.Functoid
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.reflect.{Tag, TagK}

/** @see [[https://izumi.7mind.io/distage/basics.html#subcontexts Subcontexts feature]] */
trait Subcontext[A] {
  def produce[F[_]: QuasiIO: TagK](): Lifecycle[F, A]

  /**
    * Same as `.produce[F]().use(f)`
    *
    * @note Resources allocated by the subcontext will be closed after `f` exits.
    *       Use `produce` if you need to extend the lifetime of the Subcontext's resources.
    */
  def produceRun[F[_]: QuasiIO: TagK, B](f: A => F[B]): F[B]

  final def produceRunSimple[B](f: A => B): B = produceRun[Identity, B](f)

  def provide[T: Tag](value: T)(implicit pos: CodePositionMaterializer): Subcontext[A]
  def provide[T: Tag](name: Identifier)(value: T)(implicit pos: CodePositionMaterializer): Subcontext[A]

  def plan: Plan

  final def map[B: Tag](f: A => B): Subcontext[B] = unsafeModify(_.map(f))

  /**
    * Unsafely substitute the Functoid that extracts the root component.
    *
    * Note, because the `plan` has been calculated ahead of time with `A`
    * as the root, it's not possible to request additional components
    * via Functoid that weren't already in the graph as dependencies of `A`
    * – everything that `A` doesn't depend is not in the plan.
    *
    * Example:
    *
    * {{{
    * val submodule = new ModuleDef {
    *   make[Int].fromValue(3)
    *   make[Int].named("five").from((_: Int) + 2)
    *   make[String].fromValue("x")
    * }
    *
    * makeSubcontext[Int].named("five").withSubmodule(submodule)
    *
    * ...
    *
    * (subcontext: Subcontext[Int])
    *   .unsafeModify(_ => Functoid.identity[String])
    *   .produceRun(println(_))
    *   // error: `String` is not available
    * }}}
    *
    * A binding for `String` is defined in the submodule, but is not referenced by `Int @Id("five")` binding.
    * Therefore it was removed by garbage collection and cannot be extracted with this Subcontext. You'd have to create
    * another `Subcontext[String]` with the same submodule to create `String`:
    *
    * {{{
    * makeSubcontext[String](submodule)
    *
    * ...
    *
    * (subcontext: Subcontext[String])
    *   .produceRun(println(_))
    *   // x
    * }}}
    *
    * If you DO need dynamic replanning, you'll need to use
    * [[https://izumi.7mind.io/distage/advanced-features.html#depending-on-locator nested injection]]
    * directly.
    */
  def unsafeModify[B](f: Functoid[A] => Functoid[B]): Subcontext[B]

  @deprecated("Renamed to produceRunSimple", "1.2.17")
  final def produceRun[B](f: A => B): B = produceRunSimple[B](f)
}
