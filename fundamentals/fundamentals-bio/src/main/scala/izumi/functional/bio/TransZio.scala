package izumi.functional.bio

import cats.arrow.FunctionK
import zio._

trait TransZio[F[_, _]] {
  def toZio[E]: FunctionK[F[E, ?], IO[E, ?]]
  def ofZio[E]: FunctionK[IO[E, ?], F[E, ?]]
}

object TransZio {
  def apply[F[_, _]: TransZio]: TransZio[F] = implicitly

  implicit object IdTransZio extends TransZio[IO] {
    @inline def toZio[E]: FunctionK[IO[E, ?], IO[E, ?]] = FunctionK.id
    @inline def ofZio[E]: FunctionK[IO[E, ?], IO[E, ?]] = FunctionK.id
  }
}
