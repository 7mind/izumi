package izumi.distage

import izumi.distage.model.definition.Identifier
import izumi.functional.lifecycle.Lifecycle
import izumi.distage.model.plan.Plan
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.reflect.{Tag, TagK}

trait Subcontext[A] {
  def produce[F[_]: QuasiIO: TagK](): Lifecycle[F, A]
  def produceRun[F[_]: QuasiIO: TagK](): F[A]

  def provide[T: Tag](value: T)(implicit pos: CodePositionMaterializer): Subcontext[A]
  def provide[T: Tag](name: Identifier)(value: T)(implicit pos: CodePositionMaterializer): Subcontext[A]

  def plan: Plan

  def map[B: Tag](f: A => B): Subcontext[B]
}
