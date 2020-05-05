package izumi.fundamentals.graphs.traverse

trait InterruptionStrategy[F[_]] {
  def interrupt(): F[Boolean]
}
