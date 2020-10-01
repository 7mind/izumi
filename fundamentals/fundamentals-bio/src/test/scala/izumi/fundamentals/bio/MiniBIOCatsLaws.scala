package izumi.fundamentals.bio

import cats.Eq
import cats.effect.laws.SyncLaws
import cats.effect.laws.discipline.SyncTests
import cats.effect.laws.util.TestInstances
import cats.effect.{ExitCase, Sync}
import izumi.functional.bio.impl.MiniBIO
import izumi.functional.bio.{BIO, BIOExit}
import org.scalacheck.Arbitrary
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.Checkers
import org.typelevel.discipline.scalatest.FunSuiteDiscipline

class MiniBIOCatsLaws extends AnyFunSuite with FunSuiteDiscipline with TestInstances with Checkers {

  val bio = BIO[MiniBIO]
  implicit val Sync: Sync[MiniBIO[Throwable, ?]] = new Sync[MiniBIO[Throwable, ?]] {
    override def suspend[A](thunk: => MiniBIO[Throwable, A]): MiniBIO[Throwable, A] = bio.syncThrowable(thunk).flatten

    override def bracketCase[A, B](
      acquire: MiniBIO[Throwable, A]
    )(use: A => MiniBIO[Throwable, B]
    )(release: (A, ExitCase[Throwable]) => MiniBIO[Throwable, Unit]
    ): MiniBIO[Throwable, B] = {
      bio.bracketCase[Any, Throwable, A, B](acquire)(
        (a, e) =>
          release(
            a,
            e match {
              case BIOExit.Success(_) => ExitCase.Completed
              case value: BIOExit.Failure[Throwable] => ExitCase.Error(value.toThrowable)
            },
          ).orTerminate
      )(use)
    }

    override def raiseError[A](e: Throwable): MiniBIO[Throwable, A] = bio.fail(e)

    override def handleErrorWith[A](fa: MiniBIO[Throwable, A])(f: Throwable => MiniBIO[Throwable, A]): MiniBIO[Throwable, A] = fa.catchAll(f)

    override def flatMap[A, B](fa: MiniBIO[Throwable, A])(f: A => MiniBIO[Throwable, B]): MiniBIO[Throwable, B] = fa.flatMap(f)

    override def tailRecM[A, B](a: A)(f: A => MiniBIO[Throwable, Either[A, B]]): MiniBIO[Throwable, B] = f(a).flatMap {
      case Left(a) => tailRecM(a)(f)
      case Right(b) => pure(b)
    }

    override def pure[A](x: A): MiniBIO[Throwable, A] = bio.pure(x)
  }

  val tests = new SyncTests[MiniBIO[Throwable, ?]] {
    val laws = new SyncLaws[MiniBIO[Throwable, ?]] {
      val F = Sync
    }
  }

  implicit def arb[A](implicit arb: Arbitrary[A]): Arbitrary[MiniBIO[Throwable, A]] = Arbitrary {
    Arbitrary.arbBool.arbitrary.flatMap {
      if (_)
        arb.arbitrary.map(BIO[MiniBIO].pure(_))
      else
        Arbitrary.arbThrowable.arbitrary.map(BIO[MiniBIO].fail(_))
    }
  }

  implicit def eq[A](implicit eq: Eq[A]): Eq[MiniBIO[Throwable, A]] = Eq.instance {
    (l, r) => l.run() == r.run()
  }

  checkAll("Sync[MiniBIO]", tests.sync[Int, Int, Int])
}
