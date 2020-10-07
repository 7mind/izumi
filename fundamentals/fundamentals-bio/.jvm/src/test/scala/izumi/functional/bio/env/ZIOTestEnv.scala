package izumi.functional.bio.env

import cats.Eq
import cats.effect.laws.util.TestContext
import izumi.functional.bio.BIO
import izumi.functional.bio.test.CatsLawsTestBase
import org.scalacheck.Arbitrary
import zio.Runtime
import zio.internal.{Executor, Platform, Tracing}

import scala.util.Try

trait ZIOTestEnv {
  implicit def arb[A](implicit arb: Arbitrary[A]): Arbitrary[zio.Task[A]] = Arbitrary {
    Arbitrary.arbBool.arbitrary.flatMap {
      if (_)
        arb.arbitrary.map(BIO[zio.IO].pure(_))
      else
        Arbitrary.arbThrowable.arbitrary.map(BIO[zio.IO].fail(_))
    }
  }

  implicit def eq[A](implicit eq: Eq[A]): Eq[zio.Task[A]] = Eq.instance {
    (l, r) =>
      val runtime = Runtime(
        (),
        Platform
          .fromExecutor(Executor.fromExecutionContext(Int.MaxValue)(TestContext()))
          .withTracing(Tracing.disabled)
          .withReportFailure(_ => ()),
      )
      val tl = Try(runtime.unsafeRunTask(l))
      val tr = Try(runtime.unsafeRunTask(r))
      CatsLawsTestBase.equalityTry[A].eqv(tl, tr)
  }
}
