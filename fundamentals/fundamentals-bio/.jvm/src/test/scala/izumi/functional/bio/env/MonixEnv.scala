package izumi.functional.bio.env

import cats.Eq
import cats.effect.ContextShift
import izumi.functional.bio.BIO
import izumi.functional.bio.test.CatsLawsTestBase
import monix.execution.{Scheduler, UncaughtExceptionReporter}
import org.scalacheck.Arbitrary

import scala.concurrent.ExecutionContext.global
import scala.util.Try

trait MonixEnv {
  implicit val opt = monix.bio.IO.defaultOptions
  implicit val sc = Scheduler(global, UncaughtExceptionReporter(_ => ()))
  implicit val cs = ContextShift[monix.bio.Task]

  implicit def equalityTask[A](implicit A: Eq[A]): Eq[monix.bio.Task[A]] =
    Eq.instance {
      (a, b) =>
        val ta = Try(a.runSyncUnsafeOpt())
        val tb = Try(b.runSyncUnsafeOpt())
        CatsLawsTestBase.equalityTry[A].eqv(ta, tb)
    }


  implicit def arbMonixBIO[A](implicit arb: Arbitrary[A]): Arbitrary[monix.bio.Task[A]] = Arbitrary {
    Arbitrary.arbBool.arbitrary.flatMap {
      if (_)
        arb.arbitrary.map(BIO[monix.bio.IO].pure(_))
      else
        Arbitrary.arbThrowable.arbitrary.map(BIO[monix.bio.IO].fail(_))
    }
  }
}
