package izumi.functional.bio.laws.env

import cats.Eq
import cats.effect.Effect
import cats.effect.laws.discipline.arbitrary.catsEffectLawsArbitraryForIO
import cats.effect.laws.util.{TestContext, TestInstances}
import izumi.functional.bio.{IO2, UnsafeRun2}
import org.scalacheck.{Arbitrary, Cogen}
import zio.internal.Platform
import zio.{Cause, IO, Task, UIO}

import scala.concurrent.ExecutionException

trait ZIOTestEnvTerminating {
  implicit def arbTask[A](implicit arb: Arbitrary[A]): Arbitrary[Task[A]] = Arbitrary {
    Arbitrary.arbBool.arbitrary.flatMap {
      if (_) arb.arbitrary.map(IO2[IO].pure(_))
      else Arbitrary.arbThrowable.arbitrary.map(IO2[IO].fail(_))
    }
  }

  implicit def eqTask[A](implicit eq: Eq[A]): Eq[Task[A]] = {
    Eq.by(io => UnsafeRun2.createZIO(Platform.default).unsafeRunSync(io).toThrowableEither)
  }

  implicit lazy val equalityThrowable: Eq[Throwable] = new Eq[Throwable] {
    override def eqv(x: Throwable, y: Throwable): Boolean = {
      val ex1 = extractEx(x)
      val ex2 = extractEx(y)
      ex1.getClass == ex2.getClass && ex1.getMessage == ex2.getMessage
    }

    // Unwraps exceptions that got caught by Future's implementation
    // and that got wrapped in ExecutionException (`Future(throw ex)`)
    private[this] def extractEx(ex: Throwable): Throwable =
      ex match {
        case ref: ExecutionException =>
          Option(ref.getCause).getOrElse(ref)
        case _ =>
          ex
      }
  }
}

trait ZIOTestEnvNonterminating extends TestInstances {
  implicit def arbTask[A](implicit arb: Arbitrary[A]): Arbitrary[Task[A]] = Arbitrary {
    Arbitrary.arbBool.arbitrary.flatMap {
      if (_) arb.arbitrary.map(IO2[IO].pure(_))
      else Arbitrary.arbThrowable.arbitrary.map(IO2[IO].fail(_))
    }
  }

  implicit def arbitraryCatsIO[A: Arbitrary: Cogen]: Arbitrary[cats.effect.IO[A]] =
    catsEffectLawsArbitraryForIO

  implicit lazy val zioEqCauseNothing: Eq[Cause[Nothing]] = Eq.fromUniversalEquals

  implicit def zioEqIO[E: Eq, A: Eq](implicit effect: Effect[Task], tc: TestContext): Eq[IO[E, A]] =
    Eq.by(_.either)

  implicit def zioEqTask[A: Eq](implicit effect: Effect[Task], tc: TestContext): Eq[Task[A]] =
    Eq.by(_.either)

  implicit def zioEqUIO[A: Eq](implicit effect: Effect[Task], tc: TestContext): Eq[UIO[A]] =
    Eq.by(uio => effect.toIO(uio.sandbox.either))

  implicit lazy val equalityThrowable: Eq[Throwable] = new Eq[Throwable] {
    override def eqv(x: Throwable, y: Throwable): Boolean = {
      val ex1 = extractEx(x)
      val ex2 = extractEx(y)
      ex1.getClass == ex2.getClass && ex1.getMessage == ex2.getMessage
    }

    // Unwraps exceptions that got caught by Future's implementation
    // and that got wrapped in ExecutionException (`Future(throw ex)`)
    private[this] def extractEx(ex: Throwable): Throwable =
      ex match {
        case ref: ExecutionException =>
          Option(ref.getCause).getOrElse(ref)
        case _ =>
          ex
      }
  }
}
