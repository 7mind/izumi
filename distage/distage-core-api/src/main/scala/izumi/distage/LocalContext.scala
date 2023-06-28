package izumi.distage

import izumi.distage.model.definition.Identifier
import izumi.distage.model.providers.Functoid
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.reflect.Tag

trait LocalContext[F[_]] {
  def add[T <: AnyRef: Tag](value: T)(implicit pos: CodePositionMaterializer): LocalContext[F]
  def add[T <: AnyRef: Tag](id: Identifier, value: T)(implicit pos: CodePositionMaterializer): LocalContext[F]
  def produceRun[A](function: Functoid[F[A]]): F[A]

}
