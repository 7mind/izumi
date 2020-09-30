package izumi.fundamentals.bio

import cats.Eq
import cats.effect.laws.util.{TestContext, TestInstances}
import cats.implicits._
import org.scalacheck.Arbitrary
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.prop.Configuration
import org.typelevel.discipline.scalatest.FunSuiteDiscipline
import zio.internal.{Executor, Platform, Tracing}
import zio.interop.catz.taskEffectInstance
import zio.{Runtime, ZIO}

class MiniBIOCatsLaws extends AnyFunSuite with FunSuiteDiscipline with TestInstances with Configuration with Rnd with catzSpecBaseLowPriority {
//  val syncTests = new SyncTests[MiniBIO[Throwable, ?]] {
//    val laws = new SyncLaws[MiniBIO[Throwable, ?]] {
//      val F = Sync[MiniBIO[Throwable, ?]]
//    }
//  }

//  val concurrentTestsMonix = new ConcurrentTests[monix.bio.Task] {
//    override val laws = new ConcurrentLaws[Task] {
//      val F = Concurrent[Task]
//      val contextShift = ContextShift[Task]
//    }
//  }

  implicit def rts(implicit tc: TestContext): Runtime[Unit] = Runtime(
    (),
    Platform
      .fromExecutor(Executor.fromExecutionContext(Int.MaxValue)(tc))
      .withTracing(Tracing.disabled)
      .withReportFailure(_ => ()),
  )
  implicit val zioEqCauseNothing: Eq[zio.Cause[Nothing]] = Eq.fromUniversalEquals

  implicit def zioEqUIO[A: Eq](implicit rts: Runtime[Any], tc: TestContext): Eq[zio.UIO[A]] =
    Eq.by(uio => taskEffectInstance.toIO(uio.sandbox.either))

  implicit def zioEqIO[A: Eq](implicit rts: Runtime[Any], tc: TestContext): Eq[zio.Task[A]] = Eq.by(_.either)
}

private[bio] sealed trait catzSpecBaseLowPriority { this: MiniBIOCatsLaws =>

  implicit def zioEq[R: Arbitrary, E: Eq, A: Eq](implicit rts: Runtime[Any], tc: TestContext): Eq[ZIO[R, E, A]] = {
    def run(r: R, zio: ZIO[R, E, A]) = taskEffectInstance.toIO(zio.provide(r).either)

    Eq.instance((io1, io2) => Arbitrary.arbitrary[R].sample.fold(false)(r => catsSyntaxEq(run(r, io1)) eqv run(r, io2)))
  }
}
