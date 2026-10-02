package izumi.functional.bio.laws.env

import cats.Eq
import cats.effect.kernel.Outcome
import cats.effect.testkit.TestInstances
import izumi.functional.bio.{Exit, IO2}
import izumi.functional.bio.impl.MiniBIOAsync
import izumi.fundamentals.platform.functional.Identity
import org.scalacheck.{Arbitrary, Prop}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

trait MiniBIOAsyncEnv extends TestInstances with EqThrowable {
  implicit val executionContext: ExecutionContext = ExecutionContext.global

  implicit val execMiniBIOAsync: MiniBIOAsync[Throwable, Boolean] => Prop = {
    miniBIOAsync =>
      outcome(miniBIOAsync).fold(Prop(false), Prop.exception, value => Prop(value))
  }

  implicit def arbMiniBIOAsync[A](implicit arb: Arbitrary[A]): Arbitrary[MiniBIOAsync[Throwable, A]] = Arbitrary {
    Arbitrary.arbBool.arbitrary.flatMap {
      if (_) arb.arbitrary.map(IO2[MiniBIOAsync].pure(_))
      else Arbitrary.arbThrowable.arbitrary.map(IO2[MiniBIOAsync].fail(_))
    }
  }

  implicit def eqMiniBIOAsync[A](implicit eq: Eq[A]): Eq[MiniBIOAsync[Throwable, A]] =
    Eq.by(outcome(_))

  private def outcome[A](effect: MiniBIOAsync[Throwable, A]): Outcome[Identity, Throwable, A] = {
    val runner = MiniBIOAsync.UnsafeRunMiniBIOAsync(using executionContext)
    Await.result(runner.unsafeRunAsyncAsFuture(effect), Duration.Inf) match {
      case Exit.Success(value) => Outcome.succeeded[Identity, Throwable, A](value)
      case Exit.Error(error, _) => Outcome.errored[Identity, Throwable, A](error)
      case Exit.Termination(error, _, _) => Outcome.errored[Identity, Throwable, A](error)
      case _: Exit.Interruption => Outcome.canceled[Identity, Throwable, A]
    }
  }
}
