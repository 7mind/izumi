package izumi.fundamentals.graphs.traverse

trait MPromise[F[_], P, T] {
  def status: F[MPromise.Status[P, T]]
}

object MPromise {

  sealed trait Status[+P, +T]

  case class Progress[P](p: P) extends Status[P, Nothing]

  case class Mark[T](t: T) extends Status[Nothing, T]

}
