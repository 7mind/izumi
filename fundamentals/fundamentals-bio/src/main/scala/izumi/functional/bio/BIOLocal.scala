package izumi.functional.bio

import cats.data.Kleisli

trait BIOLocal[FR[-_, +_, +_]] extends BIOMonadAsk[FR] with BIOArrowChoice[FR] with BIOLocalInstances {
  override val InnerF: BIOMonad3[FR]

  def provide[R, E, A](fr: FR[R, E, A])(env: => R): FR[Any, E, A] = contramap(fr)((_: Any) => env)

  // defaults
  override def dimap[R1, E, A1, R2, A2](fab: FR[R1, E, A1])(f: R2 => R1)(g: A1 => A2): FR[R2, E, A2] = InnerF.map(contramap(fab)(f))(g)
  override def choose[RL, RR, E, AL, AR](f: FR[RL, E, AL], g: FR[RR, E, AR]): FR[Either[RL, RR], E, Either[AL, AR]] = access {
    case Left(a) => InnerF.map(provide(f)(a))(Left(_))
    case Right(b) => InnerF.map(provide(g)(b))(Right(_)): FR[Either[RL, RR], E, Either[AL, AR]]
  }
  override def askWith[R, A](f: R => A): FR[R, Nothing, A] = InnerF.map(ask[R])(f)
  override def andThen[R, R1, E, A](f: FR[R, E, R1], g: FR[R1, E, A]): FR[R, E, A] = InnerF.flatMap(f)(provide(g)(_))
  override def asking[R, E, A](f: FR[R, E, A]): FR[R, E, (A, R)] = InnerF.map2(ask[R], f)((r, a) => (a, r))
  override def access[R, E, A](f: R => FR[R, E, A]): FR[R, E, A] = InnerF.flatMap(ask[R])(f)
}

private[bio] sealed trait BIOLocalInstances
object BIOLocalInstances {
  implicit final class ToKleisliSyntaxLocal[FR[-_, +_, +_]](private val FR: BIOLocal[FR]) extends AnyVal {
    @inline final def toKleisli[R, E, A](fr: FR[R, E, A]): Kleisli[FR[Any, E, ?], R, A] = Kleisli(FR.provide(fr)(_))
  }
}
