package izumi.distage

import izumi.distage.model.definition.Identifier
import izumi.functional.lifecycle.Lifecycle
import izumi.distage.model.plan.Plan
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.reflect.{Tag, TagK}

/** @see [[https://izumi.7mind.io/distage/basics.html#subcontexts Subcontexts feature]] */
trait Subcontext[A] {
  def produce[F[_]: QuasiIO: TagK](): Lifecycle[F, A]
  def produceRun[F[_]: QuasiIO: TagK, B](f: A => F[B]): F[B]
  final def produceRun[B](f: A => B): B = produceRun[Identity, B](f)

  def provide[T: Tag](value: T)(implicit pos: CodePositionMaterializer): Subcontext[A]
  def provide[T: Tag](name: Identifier)(value: T)(implicit pos: CodePositionMaterializer): Subcontext[A]

  def plan: Plan

  def map[B: Tag](f: A => B): Subcontext[B]
}
