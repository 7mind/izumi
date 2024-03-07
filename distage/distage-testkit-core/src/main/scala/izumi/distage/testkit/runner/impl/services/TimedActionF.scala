package izumi.distage.testkit.runner.impl.services

import distage.*
import izumi.functional.bio.Clock1
import izumi.functional.quasi.QuasiIO
import izumi.functional.quasi.QuasiIO.syntax.*

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
      val before = Clock1.Standard.nowOffset()
      val value = action
      val after = Clock1.Standard.nowOffset()
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
        before <- Lifecycle.liftF(F.maybeSuspend(Clock1.Standard.nowOffset()))
        value <- action
        after <- Lifecycle.liftF(F.maybeSuspend(Clock1.Standard.nowOffset()))
      } yield {
        Timed(value, Timing(begin = before, duration = FiniteDuration(ChronoUnit.NANOS.between(before, after), TimeUnit.NANOSECONDS)))
      }
    }

    override def apply[A](action: => F[A]): F[Timed[A]] = {
      for {
        before <- F.maybeSuspend(Clock1.Standard.nowOffset())
        value <- action
        after <- F.maybeSuspend(Clock1.Standard.nowOffset())
      } yield {
        Timed(value, Timing(begin = before, duration = FiniteDuration(ChronoUnit.NANOS.between(before, after), TimeUnit.NANOSECONDS)))
      }
    }

  }
}
