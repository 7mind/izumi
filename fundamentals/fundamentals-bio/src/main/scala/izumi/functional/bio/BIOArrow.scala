package izumi.functional.bio

trait BIOArrow[FR[-_, +_, +_]] extends BIOProfunctor[FR] {
  @inline final def choise[RL, RR, E, A](f: FR[RL, E, A], g: FR[RR, E, A]): FR[Either[RL, RR], E, A] = {
    access[Either[RL, RR], E, A] {
      case Left(a)  => provide(f)(a)
      case Right(b) => provide(g)(b)
    }
  }
  @inline final def choose[RL, RR, E, AL, AR](f: FR[RL, E, AL], g: FR[RR, E, AR]): FR[Either[RL, RR], E, Either[AL, AR]] = {
    access[Either[RL, RR], E, Either[AL, AR]] {
      case Left(a)  => InnerF.map(provide(f)(a))(Left(_))
      case Right(b) => InnerF.map(provide(g)(b))(Right(_))
    }
  }
  @inline final def andThen[R1, R2, E, A](f: FR[R1, E, R2], g: FR[R2, E, A]): FR[R1, E, A] = InnerF.flatMap(f)(provide(g)(_))
}