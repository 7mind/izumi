package izumi.functional.bio

import izumi.functional.bio.PredefinedHelper.Predefined
import zio.ZIO

trait Fork3[F[-_, +_, +_]] extends RootBifunctor[F] with ForkInstances {
  def fork[R, E, A](f: F[R, E, A]): F[R, Nothing, Fiber3[F, E, A]]
}

private[bio] sealed trait ForkInstances
object ForkInstances extends LowPriorityForkInstances {
  @inline implicit def ForkZio: Predefined.Of[Fork3[ZIO]] = Predefined(impl.ForkZio)
}
sealed trait LowPriorityForkInstances {
//  @inline implicit def ForkMonix[MonixBIO[+_, +_]: `monix.bio.IO`]: Predefined.Of[Fork2[MonixBIO]] = impl.ForkMonix.asInstanceOf[Predefined.Of[Fork2[MonixBIO]]]
}
