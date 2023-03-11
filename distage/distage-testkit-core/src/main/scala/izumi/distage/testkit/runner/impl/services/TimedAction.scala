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
    def mapMerge[O](left: (A, Timing) => O, right: (B, Timing) => O): O = timed.out match {
      case Left(value) => left(value, timed.timing)
      case Right(value) => right(value, timed.timing)
    }
  }
}

trait TimedAction[F[_]] {
  def timed[A](action: => A): Timed[A]
  def timed[A](action: => F[A]): F[Timed[A]]
  def timed[A](action: => Lifecycle[F, A]): Lifecycle[F, Timed[A]]
}

object TimedAction {
  class TimedActionImpl[F[_]]()(implicit F: QuasiIO[F]) extends TimedAction[F] {
    override def timed[A](action: => Lifecycle[F, A]): Lifecycle[F, Timed[A]] = {
      for {
        before <- Lifecycle.liftF(F.maybeSuspend(IzTime.utcNowOffset))
        value <- action
        after <- Lifecycle.liftF(F.maybeSuspend(IzTime.utcNowOffset))
      } yield {
        Timed(value, Timing(begin = before, duration = FiniteDuration(ChronoUnit.NANOS.between(before, after), TimeUnit.NANOSECONDS)))
      }
    }

    override def timed[A](action: => F[A]): F[Timed[A]] = {
      for {
        before <- F.maybeSuspend(IzTime.utcNowOffset)
        value <- action
        after <- F.maybeSuspend(IzTime.utcNowOffset)
      } yield {
        Timed(value, Timing(begin = before, duration = FiniteDuration(ChronoUnit.NANOS.between(before, after), TimeUnit.NANOSECONDS)))
      }
    }

    override def timed[A](action: => A): Timed[A] = {
      val before = IzTime.utcNowOffset
      val value = action
      val after = IzTime.utcNowOffset
      Timed(value, Timing(begin = before, duration = FiniteDuration(ChronoUnit.NANOS.between(before, after), TimeUnit.NANOSECONDS)))
    }
  }
}
