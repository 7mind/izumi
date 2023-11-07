package izumi.functional.bio

import izumi.functional.bio.PredefinedHelper.Predefined
import izumi.fundamentals.orphans.`zio.ZIO`

import scala.concurrent.ExecutionContext

trait Fork2[F[+_, +_]] extends RootBifunctor[F] with ForkInstances {
  def fork[E, A](f: F[E, A]): F[Nothing, Fiber2[F, E, A]]
  def forkOn[E, A](ec: ExecutionContext)(f: F[E, A]): F[Nothing, Fiber2[F, E, A]]
}

private[bio] sealed trait ForkInstances
object ForkInstances extends LowPriorityForkInstances {
  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have zio-core as a dependency without REQUIRING a zio-core dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  @inline implicit def ForkZio[ZIO[-_, +_, +_]: `zio.ZIO`]: Predefined.Of[Fork2[ZIO[Any, +_, +_]]] = Predefined(impl.ForkZio.asInstanceOf[Fork2[ZIO[Any, +_, +_]]])
}
sealed trait LowPriorityForkInstances {
  @inline implicit def ForkZioR[ZIO[-_, +_, +_]: `zio.ZIO`, R]: Predefined.Of[Fork2[ZIO[R, +_, +_]]] = Predefined(impl.ForkZio.asInstanceOf[Fork2[ZIO[R, +_, +_]]])
//  @inline implicit def ForkMonix[MonixBIO[+_, +_]: `monix.bio.IO`]: Predefined.Of[Fork2[MonixBIO]] = impl.ForkMonix.asInstanceOf[Predefined.Of[Fork2[MonixBIO]]]
}
