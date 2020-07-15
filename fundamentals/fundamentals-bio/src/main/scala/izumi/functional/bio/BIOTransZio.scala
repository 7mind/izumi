package izumi.functional.bio

import cats.arrow.FunctionK
import zio._

trait BIOTransZio[F[_, _]] {
  def toZio[E]: FunctionK[F[E, ?], IO[E, ?]]
  def ofZio[E]: FunctionK[IO[E, ?], F[E, ?]]
}

object BIOTransZio {
  def apply[F[_, _]: BIOTransZio]: BIOTransZio[F] = implicitly

  implicit object IdTransZio extends BIOTransZio[IO] {
    @inline def toZio[E]: FunctionK[IO[E, ?], IO[E, ?]] = FunctionK.id
    @inline def ofZio[E]: FunctionK[IO[E, ?], IO[E, ?]] = FunctionK.id
  }
}
