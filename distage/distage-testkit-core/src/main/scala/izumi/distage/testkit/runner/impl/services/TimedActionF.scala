package izumi.distage.testkit.runner.impl.services

import distage.*
import izumi.functional.bio.Clock1
import izumi.functional.bio.{IO2, Primitives2}

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

final case class Timed[A](out: A, timing: Timing)
object Timed {
  def fromDiff[A](out: A, before: OffsetDateTime, after: OffsetDateTime): Timed[A] = {
    Timed(out, Timing.fromDiff(before, after))
  }

  implicit class TimedEitherExt[A, B](val timed: Timed[Either[A, B]]) {
    def invert: Either[Timed[A], Timed[B]] = {
      timed.out match {
        case Left(value) =>
          Left(Timed(value, timed.timing))

        case Right(value) =>
          Right(Timed(value, timed.timing))
      }
    }

    def foldEither[O](left: (A, Timing) => O, right: (B, Timing) => O): O = timed.out match {
      case Left(value) => left(value, timed.timing)
      case Right(value) => right(value, timed.timing)
    }
  }
}

final case class Timing(begin: OffsetDateTime, duration: FiniteDuration)
object Timing {
  def fromDiff(before: OffsetDateTime, after: OffsetDateTime): Timing = {
    Timing(begin = before, duration = FiniteDuration(ChronoUnit.NANOS.between(before, after), TimeUnit.NANOSECONDS))
  }
}

trait TimedActionF[F[+_, +_]] {
  def timed[E, A](action: => F[E, A]): F[E, Timed[A]]
  def timedLifecycle[E, A](action: => Lifecycle[F, E, A]): Lifecycle[F, E, Timed[A]]
  def timedWith[E, A](action: (() => F[Nothing, Timing]) => F[E, A]): F[E, Timed[A]]
}

object TimedActionF {
  class TimedActionFImpl[F[+_, +_]]()(implicit F: IO2[F], FP: Primitives2[F]) extends TimedActionF[F] {
    override def timedLifecycle[E, A](action: => Lifecycle[F, E, A]): Lifecycle[F, E, Timed[A]] = {
      for {
        before <- Lifecycle.liftF[F, E, OffsetDateTime](F.sync(Clock1.Standard.nowOffset()))
        value <- action
        after <- Lifecycle.liftF[F, E, OffsetDateTime](F.sync(Clock1.Standard.nowOffset()))
      } yield {
        Timed.fromDiff(value, before, after)
      }
    }

    override def timed[E, A](action: => F[E, A]): F[E, Timed[A]] = {
      F.flatMap(F.sync(Clock1.Standard.nowOffset())) { before =>
        F.flatMap(action) { value =>
          F.map(F.sync(Clock1.Standard.nowOffset())) { after =>
            Timed.fromDiff(value, before, after)
          }
        }
      }
    }

    override def timedWith[E, A](action: (() => F[Nothing, Timing]) => F[E, A]): F[E, Timed[A]] = {
      F.flatMap(F.sync(Clock1.Standard.nowOffset())) { before =>
        F.flatMap(
          action(() =>
            F.sync {
              val current = Clock1.Standard.nowOffset()
              Timing.fromDiff(before, current)
            }
          )
        ) { value =>
          F.map(F.sync(Clock1.Standard.nowOffset())) { after =>
            Timed.fromDiff(value, before, after)
          }
        }
      }
    }
  }
}
