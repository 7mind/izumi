package izumi.distage.testkit.runner.impl.services

import distage.*
import izumi.functional.bio.Clock1
import izumi.functional.quasi.QuasiIO
import izumi.functional.quasi.QuasiIO.syntax.*

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

final case class Timing(begin: OffsetDateTime, duration: FiniteDuration) {
  lazy val end: OffsetDateTime = begin.plusNanos(duration.toNanos)

  def ++(other: Timing): Timing = {
    val combinedBegin = if (begin.isBefore(other.begin)) begin else other.begin
    val combinedEnd = if (end.isAfter(other.end)) end else other.end
    Timing.fromDiff(combinedBegin, combinedEnd)
  }
}
object Timing {
  def fromDiff(before: OffsetDateTime, after: OffsetDateTime): Timing = {
    Timing(begin = before, duration = FiniteDuration(ChronoUnit.NANOS.between(before, after), TimeUnit.NANOSECONDS))
  }
}

trait TimedActionF[F[_]] {
  def timed[A](action: => F[A]): F[Timed[A]]
  def timedLifecycle[A](action: => Lifecycle[F, A]): Lifecycle[F, Timed[A]]
  def timedWith[A](action: (() => F[Timing]) => F[A]): F[Timed[A]]
}

object TimedActionF {
  class TimedActionFImpl[F[_]]()(implicit F: QuasiIO[F]) extends TimedActionF[F] {
    override def timedLifecycle[A](action: => Lifecycle[F, A]): Lifecycle[F, Timed[A]] = {
      for {
        before <- Lifecycle.liftF(F.maybeSuspend(Clock1.Standard.nowOffset()))
        value <- action
        after <- Lifecycle.liftF(F.maybeSuspend(Clock1.Standard.nowOffset()))
      } yield {
        Timed.fromDiff(value, before, after)
      }
    }

    override def timed[A](action: => F[A]): F[Timed[A]] = {
      for {
        before <- F.maybeSuspend(Clock1.Standard.nowOffset())
        value <- action
        after <- F.maybeSuspend(Clock1.Standard.nowOffset())
      } yield {
        Timed.fromDiff(value, before, after)
      }
    }

    override def timedWith[A](action: (() => F[Timing]) => F[A]): F[Timed[A]] = {
      for {
        before <- F.maybeSuspend(Clock1.Standard.nowOffset())
        value <- action(
          () =>
            F.maybeSuspend {
              val current = Clock1.Standard.nowOffset()
              Timing.fromDiff(before, current)
            }
        )
        after <- F.maybeSuspend(Clock1.Standard.nowOffset())
      } yield {
        Timed.fromDiff(value, before, after)
      }
    }
  }
}
