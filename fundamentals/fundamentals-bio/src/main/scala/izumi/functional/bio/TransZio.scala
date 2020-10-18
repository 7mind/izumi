package izumi.functional.bio

import izumi.functional.bio.data.{FunctionKK, ~>>}
import zio._

trait TransZio[F[_, _]] {
  def toZio: F ~>> IO
  def ofZio: IO ~>> F
}

object TransZio {
  def apply[F[_, _]: TransZio]: TransZio[F] = implicitly

  implicit object IdTransZio extends TransZio[IO] {
    @inline def toZio: IO ~>> IO = FunctionKK.id
    @inline def ofZio: IO ~>> IO = FunctionKK.id
  }
}
