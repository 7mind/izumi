package izumi.distage.testkit.runner.impl.services

import distage.*
import izumi.functional.quasi.QuasiIO
import izumi.functional.quasi.QuasiIO.syntax.*
import izumi.fundamentals.platform.time.IzTime

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

case class Timing(begin: OffsetDateTime, duration: FiniteDuration)
case class Timed[A](out: A, timing: Timing)

object Timed {
  implicit class TimedEitherExt[A, B](val timed: Timed[Either[A, B]]) {
    def invert: Either[Timed[A], Timed[B]] = {
      timed.out match {
        case Left(value) =>
          Left(Timed(value, timed.timing))

        case Right(value) =>
          Right(Timed(value, timed.timing))
      }
    }

    def mapMerge[O](left: (A, Timing) => O, right: (B, Timing) => O): O = timed.out match {
      case Left(value) => left(value, timed.timing)
      case Right(value) => right(value, timed.timing)
    }
  }
}

trait TimedAction {
  def apply[A](action: => A): Timed[A]
}

object TimedAction {
  class TimedActionImpl() extends TimedAction {
    override def apply[A](action: => A): Timed[A] = {
      val before = IzTime.utcNowOffset
      val value = action
      val after = IzTime.utcNowOffset
      Timed(value, Timing(begin = before, duration = FiniteDuration(ChronoUnit.NANOS.between(before, after), TimeUnit.NANOSECONDS)))
    }
  }
}

trait TimedActionF[F[_]] {
  def apply[A](action: => F[A]): F[Timed[A]]
  def apply[A](action: => Lifecycle[F, A]): Lifecycle[F, Timed[A]]
}

object TimedActionF {
  class TimedActionFImpl[F[_]]()(implicit F: QuasiIO[F]) extends TimedActionF[F] {
    override def apply[A](action: => Lifecycle[F, A]): Lifecycle[F, Timed[A]] = {
      for {
        before <- Lifecycle.liftF(F.maybeSuspend(IzTime.utcNowOffset))
        value <- action
        after <- Lifecycle.liftF(F.maybeSuspend(IzTime.utcNowOffset))
      } yield {
        Timed(value, Timing(begin = before, duration = FiniteDuration(ChronoUnit.NANOS.between(before, after), TimeUnit.NANOSECONDS)))
      }
    }

    override def apply[A](action: => F[A]): F[Timed[A]] = {
      for {
        before <- F.maybeSuspend(IzTime.utcNowOffset)
        value <- action
        after <- F.maybeSuspend(IzTime.utcNowOffset)
      } yield {
        Timed(value, Timing(begin = before, duration = FiniteDuration(ChronoUnit.NANOS.between(before, after), TimeUnit.NANOSECONDS)))
      }
    }

  }
}
