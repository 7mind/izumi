package izumi.functional.bio

/** Scala.js does not support BIORunner */
trait BIORunner[F[_, _]]

object BIORunner {
  @inline def apply[F[_, _]](implicit ev: BIORunner[F]): BIORunner[F] = ev

  implicit def anyBIORunner[F[_, _]]: BIORunner[F] = new BIORunner[F] {}
}
