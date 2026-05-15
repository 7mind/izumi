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
  def fromDiff[A](out: A, before: TimingStart, after: OffsetDateTime): Timed[A] = {
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

/** Captures a wall-clock moment together with the thread that observed it.
  *
  * The thread name is recorded for downstream reporters (notably the ScalaTest event
  * stream) that need to attribute work to a specific JVM thread. Both fields are sampled
  * within the same `F.maybeSuspend` so they describe the same observation.
  */
final case class TimingStart(at: OffsetDateTime, threadName: String)

/** A measured time interval: begin moment, total duration, and the thread that
  * began the measurement.
  *
  * `threadName` is captured at `begin`. For phases that may shift threads (e.g. async
  * test execution under cats-effect/ZIO), this records the thread that the phase
  * started on — which is the right attribution for the ScalaTest event corresponding
  * to that phase's start. Phase-end timing on a different thread can be recovered via
  * a follow-up [[TimingStart]] capture if needed.
  */
final case class Timing(begin: OffsetDateTime, duration: FiniteDuration, threadName: String) {
  def end: OffsetDateTime = begin.plusNanos(duration.toNanos)
}
object Timing {
  def fromDiff(before: TimingStart, after: OffsetDateTime): Timing = {
    Timing(
      begin = before.at,
      duration = FiniteDuration(ChronoUnit.NANOS.between(before.at, after), TimeUnit.NANOSECONDS),
      threadName = before.threadName,
    )
  }
}

trait TimedActionF[F[_]] {
  def timed[A](action: => F[A]): F[Timed[A]]
  def timedLifecycle[A](action: => Lifecycle[F, A]): Lifecycle[F, Timed[A]]
  def timedWith[A](action: (() => F[Timing]) => F[A]): F[Timed[A]]
}

object TimedActionF {
  class TimedActionFImpl[F[_]]()(implicit F: QuasiIO[F]) extends TimedActionF[F] {
    private def sampleStart: TimingStart = TimingStart(Clock1.Standard.nowOffset(), Thread.currentThread.getName)

    override def timedLifecycle[A](action: => Lifecycle[F, A]): Lifecycle[F, Timed[A]] = {
      for {
        before <- Lifecycle.liftF(F.maybeSuspend(sampleStart))
        value <- action
        after <- Lifecycle.liftF(F.maybeSuspend(Clock1.Standard.nowOffset()))
      } yield {
        Timed.fromDiff(value, before, after)
      }
    }

    override def timed[A](action: => F[A]): F[Timed[A]] = {
      for {
        before <- F.maybeSuspend(sampleStart)
        value <- action
        after <- F.maybeSuspend(Clock1.Standard.nowOffset())
      } yield {
        Timed.fromDiff(value, before, after)
      }
    }

    override def timedWith[A](action: (() => F[Timing]) => F[A]): F[Timed[A]] = {
      for {
        before <- F.maybeSuspend(sampleStart)
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
