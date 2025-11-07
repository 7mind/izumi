package izumi.functional.bio.laws.env

import cats.Eq
import cats.effect.SyncIO
import cats.effect.testkit.TestInstances
import izumi.functional.bio.{Exit, IO2}
import izumi.functional.bio.impl.MiniBIOAsync
import org.scalacheck.{Arbitrary, Prop}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}
import scala.util.Try

trait MiniBIOAsyncEnv extends TestInstances with EqThrowable {
  implicit val executionContext: ExecutionContext = ExecutionContext.global

  implicit val execMiniBIOAsync: MiniBIOAsync[Throwable, Boolean] => Prop = {
    miniBIOAsync =>
      val futureExit = miniBIOAsync.runOnEC(executionContext)
      val tryResult = Try {
        Await.result(futureExit, Duration.Inf) match {
          case Exit.Success(value) => value
          case Exit.Error(e, _) => throw e
          case Exit.Termination(t, _, _) => throw t
        }
      }
      syncIoBooleanToProp(SyncIO.fromTry(tryResult))
  }

  implicit def arbMiniBIOAsync[A](implicit arb: Arbitrary[A]): Arbitrary[MiniBIOAsync[Throwable, A]] = Arbitrary {
    Arbitrary.arbBool.arbitrary.flatMap {
      if (_) arb.arbitrary.map(IO2[MiniBIOAsync].pure(_))
      else Arbitrary.arbThrowable.arbitrary.map(IO2[MiniBIOAsync].fail(_))
    }
  }

  implicit def eqMiniBIOAsync[A](implicit eq: Eq[A]): Eq[MiniBIOAsync[Throwable, A]] = Eq.instance {
    (l, r) =>
      val futureL = l.runOnEC(executionContext)
      val futureR = r.runOnEC(executionContext)
      val tl = Try {
        Await.result(futureL, Duration.Inf) match {
          case Exit.Success(value) => value
          case Exit.Error(e, _) => throw e
          case Exit.Termination(t, _, _) => throw t
        }
      }
      val tr = Try {
        Await.result(futureR, Duration.Inf) match {
          case Exit.Success(value) => value
          case Exit.Error(e, _) => throw e
          case Exit.Termination(t, _, _) => throw t
        }
      }
      equalityTry[A].eqv(tl, tr)
  }

  private def equalityTry[A: Eq]: Eq[Try[A]] =
    Eq.by(_.toEither)
}
