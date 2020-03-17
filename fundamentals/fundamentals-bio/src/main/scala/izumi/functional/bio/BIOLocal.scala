package izumi.functional.bio

import cats.data.Kleisli

trait BIOLocal[FR[-_, +_, +_]] extends BIOMonadAsk[FR] with BIOArrowChoice[FR] with BIOLocalInstances {
  override val InnerF: BIOMonad3[FR]

  def provide[R, E, A](fr: FR[R, E, A])(env: => R): FR[Any, E, A] = contramap(fr)((_: Any) => env)

  // defaults
  override def dimap[R1, E, A1, R2, A2](fab: FR[R1, E, A1])(f: R2 => R1)(g: A1 => A2): FR[R2, E, A2] = InnerF.map(contramap(fab)(f))(g)
  override def choose[RL, RR, E, AL, AR](f: FR[RL, E, AL], g: FR[RR, E, AR]): FR[Either[RL, RR], E, Either[AL, AR]] = access {
    case Left(a)  => InnerF.map(provide(f)(a))(Left(_))
    case Right(b) => InnerF.map(provide(g)(b))(Right(_)): FR[Either[RL, RR], E, Either[AL, AR]]
  }
}

private[bio] sealed trait BIOLocalInstances
object BIOLocalInstances {
  implicit final class ToKleisliSyntaxLocal[FR[-_, +_, +_]](private val FR: BIOLocal[FR]) extends AnyVal {
    @inline final def toKleisli[R, E, A](fr: FR[R, E, A]): Kleisli[FR[Any, E, ?], R, A] = Kleisli(FR.provide(fr)(_))
  }
}
