package izumi.distage

import izumi.distage.model.definition.Identifier
import izumi.distage.model.plan.Plan
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.reflect.Tag

trait AnyLocalContext[F[_]]

trait LocalContext[F[_], R] extends AnyLocalContext[F] {
  def provide[T: Tag](value: T)(implicit pos: CodePositionMaterializer): LocalContext[F, R]
  def provideNamed[T: Tag](id: Identifier, value: T)(implicit pos: CodePositionMaterializer): LocalContext[F, R]
  def produceRun(): F[R]
  def plan: Plan
}
