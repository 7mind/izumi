package izumi.functional.bio

/** Scala.js does not support UnsafeRun */
trait UnsafeRun2[F[_, _]]

object UnsafeRun2 {
  @inline def apply[F[_, _]](implicit ev: UnsafeRun2[F]): UnsafeRun2[F] = ev

  implicit def anyUnsafeRun2[F[_, _]]: UnsafeRun2[F] = new UnsafeRun2[F] {}

  sealed trait FailureHandler
  object FailureHandler {
    final case object Default extends FailureHandler
    final case class Custom(handler: Exit.Failure[Any] => Unit) extends FailureHandler
  }
}
