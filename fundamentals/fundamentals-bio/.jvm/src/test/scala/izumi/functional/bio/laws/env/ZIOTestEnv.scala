package izumi.functional.bio.laws.env

import cats.Eq
import cats.effect.Effect
import cats.effect.laws.discipline.arbitrary.catsEffectLawsArbitraryForIO
import cats.effect.laws.util.{TestContext, TestInstances}
import izumi.functional.bio.IO2
import org.scalacheck.{Arbitrary, Cogen}
import zio.{Cause, IO, Task, UIO}

trait ZIOTestEnv extends TestInstances {
  implicit def arbTask[A](implicit arb: Arbitrary[A]): Arbitrary[zio.Task[A]] = Arbitrary {
    Arbitrary.arbBool.arbitrary.flatMap {
      if (_) arb.arbitrary.map(IO2[zio.IO].pure(_))
      else Arbitrary.arbThrowable.arbitrary.map(IO2[zio.IO].fail(_))
    }
  }

  implicit def arbitraryCatsIO[A: Arbitrary: Cogen]: Arbitrary[cats.effect.IO[A]] =
    catsEffectLawsArbitraryForIO

  implicit val zioEqCauseNothing: Eq[Cause[Nothing]] = Eq.fromUniversalEquals

  implicit def zioEqIO[E: Eq, A: Eq](implicit effect: Effect[Task], tc: TestContext): Eq[IO[E, A]] =
    Eq.by(_.either)

  implicit def zioEqTask[A: Eq](implicit effect: Effect[Task], tc: TestContext): Eq[Task[A]] =
    Eq.by(_.either)

  implicit def zioEqUIO[A: Eq](implicit effect: Effect[Task], tc: TestContext): Eq[UIO[A]] =
    Eq.by(uio => effect.toIO(uio.sandbox.either))
}
