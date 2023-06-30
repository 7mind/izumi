package izumi.distage

import izumi.distage.model.definition.Identifier
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.reflect.Tag

trait AnyLocalContext[F[_]]

trait LocalContext[F[_], R] extends AnyLocalContext[F] {
  def provide[T <: Any: Tag](value: T)(implicit pos: CodePositionMaterializer): LocalContext[F, R]
  def add[T <: Any: Tag](id: Identifier, value: T)(implicit pos: CodePositionMaterializer): LocalContext[F, R]
  def produceRun(): F[R]
}
