package izumi.functional.bio.retry

import izumi.functional.bio.retry.RetryPolicy.{ControllerDecision, RetryFunction}
import izumi.functional.bio.{Applicative2, F, Functor2, Monad2}

import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class RetryPolicy[F[+_, +_], -A, +B](val action: RetryFunction[F, A, B]) {
  def &&[A1 <: A, B1](policy: RetryPolicy[F, A1, B1])(implicit F: Applicative2[F]): RetryPolicy[F, A1, (B, B1)] = {
    def combined(left: RetryFunction[F, A, B], right: RetryFunction[F, A1, B1]): RetryFunction[F, A1, (B, B1)] =
      (now: ZonedDateTime, in: A1) =>
        F.map2(left(now, in), right(now, in)) {
          case (ControllerDecision.Stop(l), ControllerDecision.Stop(r)) => ControllerDecision.Stop(l -> r)
          case (ControllerDecision.Repeat(l, _, _), ControllerDecision.Stop(r)) => ControllerDecision.Stop(l -> r)
          case (ControllerDecision.Stop(l), ControllerDecision.Repeat(r, _, _)) => ControllerDecision.Stop(l -> r)
          case (ControllerDecision.Repeat(l, linterval, lnext), ControllerDecision.Repeat(r, rinterval, rnext)) =>
            val combinedInterval = if (linterval.toInstant.compareTo(rinterval.toInstant) >= 0) linterval else rinterval
            ControllerDecision.Repeat(l -> r, combinedInterval, combined(lnext, rnext))
        }
    RetryPolicy(combined(action, policy.action))
  }

  def ||[A1 <: A, B1](policy: RetryPolicy[F, A1, B1])(implicit F: Applicative2[F]): RetryPolicy[F, A1, (B, B1)] = {
    def combined(left: RetryFunction[F, A, B], right: RetryFunction[F, A1, B1]): RetryFunction[F, A1, (B, B1)] = {
      (now: ZonedDateTime, in: A1) =>
        F.map2(left(now, in), right(now, in)) {
          case (ControllerDecision.Repeat(l, linterval, lnext), ControllerDecision.Repeat(r, rinterval, rnext)) =>
            val combinedInterval = if (linterval.toInstant.compareTo(rinterval.toInstant) < 0) linterval else rinterval
            ControllerDecision.Repeat(l -> r, combinedInterval, combined(lnext, rnext))
          case (ControllerDecision.Repeat(l, interval, next), ControllerDecision.Stop(r)) =>
            ControllerDecision.Repeat(l -> r, interval, combined(next, RetryFunction.done(r)))
          case (ControllerDecision.Stop(l), ControllerDecision.Repeat(r, interval, next)) =>
            ControllerDecision.Repeat(l -> r, interval, combined(RetryFunction.done(l), next))
          case (ControllerDecision.Stop(l), ControllerDecision.Stop(r)) => ControllerDecision.Stop(l -> r)
        }
    }
    RetryPolicy(combined(action, policy.action))
  }

  def >>>[B1](that: RetryPolicy[F, B, B1])(implicit F: Monad2[F]): RetryPolicy[F, A, B1] = {
    def loop(self: RetryFunction[F, A, B], that: RetryFunction[F, B, B1]): RetryFunction[F, A, B1] = {
      (now: ZonedDateTime, in: A) =>
        self(now, in).flatMap {
          case ControllerDecision.Stop(out) =>
            that(now, out).map {
              case ControllerDecision.Repeat(out, _, _) => ControllerDecision.Stop(out)
              case stop @ ControllerDecision.Stop(_) => stop
            }
          case ControllerDecision.Repeat(out, interval, action) =>
            that(now, out).map {
              case stop @ ControllerDecision.Stop(_) => stop
              case ControllerDecision.Repeat(out2, interval2, action2) =>
                val combined = if (interval.toInstant.compareTo(interval2.toInstant) >= 0) interval else interval2
                ControllerDecision.Repeat(out2, combined, loop(action, action2))
            }
        }
    }
    RetryPolicy(loop(action, that.action))
  }

  def whileInput[A1 <: A](f: A1 => Boolean)(implicit F: Applicative2[F]): RetryPolicy[F, A1, B] = {
    check((in, _) => f(in))
  }

  def whileOutput(f: B => Boolean)(implicit F: Applicative2[F]): RetryPolicy[F, A, B] = {
    check((_, out) => f(out))
  }

  def check[A1 <: A](pred: (A1, B) => Boolean)(implicit F: Functor2[F]): RetryPolicy[F, A1, B] = {
    def loop(action: RetryFunction[F, A1, B]): RetryFunction[F, A1, B] = {
      (now: ZonedDateTime, in: A1) =>
        action(now, in).map {
          case ControllerDecision.Repeat(out, interval, next) =>
            if (pred(in, out)) ControllerDecision.Repeat(out, interval, loop(next)) else ControllerDecision.Stop(out)
          case stop => stop
        }
    }
    RetryPolicy(loop(action))
  }

  def modifyDelay(f: B => FiniteDuration)(implicit F: Functor2[F]): RetryPolicy[F, A, B] = {
    def loop(action: RetryFunction[F, A, B]): RetryFunction[F, A, B] = {
      (now, in) =>
        action(now, in).map {
          case ControllerDecision.Repeat(out, interval, next) =>
            val sleepTime = f(out)
            ControllerDecision.Repeat(out, interval.plusNanos(sleepTime.toNanos), loop(next))
          case stop => stop
        }
    }
    RetryPolicy(loop(action))
  }

  def map[B1](f: B => B1)(implicit F: Functor2[F]): RetryPolicy[F, A, B1] = {
    def loop(action: RetryFunction[F, A, B]): RetryFunction[F, A, B1] = {
      (now, in) =>
        action(now, in).map {
          case ControllerDecision.Repeat(out, interval, next) => ControllerDecision.Repeat(f(out), interval, loop(next))
          case ControllerDecision.Stop(out) => ControllerDecision.Stop(f(out))
        }
    }
    RetryPolicy(loop(action))
  }

}

object RetryPolicy {
  private val LongMax: BigInt = BigInt(Long.MaxValue)

  def apply[F[+_, +_], A, B](action: RetryFunction[F, A, B]): RetryPolicy[F, A, B] = new RetryPolicy[F, A, B](action)

  def identity[F[+_, +_]: Applicative2, A]: RetryPolicy[F, A, A] = {
    lazy val loop: RetryFunction[F, A, A] = {
      (now: ZonedDateTime, in: A) =>
        F.pure(ControllerDecision.Repeat(in, now, loop))
    }
    RetryPolicy(loop)
  }

  def giveUp[F[+_, +_]: Applicative2]: RetryPolicy[F, Any, Unit] = {
    RetryPolicy(RetryFunction.done(()))
  }

  def elapsed[F[+_, +_]: Applicative2]: RetryPolicy[F, Any, FiniteDuration] = {
    def loop(start: Option[ZonedDateTime]): RetryFunction[F, Any, FiniteDuration] = {
      (now, _) =>
        F.pure {
          start match {
            case Some(start) =>
              val duration = FiniteDuration(now.toInstant.toEpochMilli - start.toInstant.toEpochMilli, TimeUnit.MILLISECONDS)
              ControllerDecision.Repeat(duration, now, loop(Some(start)))
            case None => ControllerDecision.Repeat(FiniteDuration(0, TimeUnit.MILLISECONDS), now, loop(Some(now)))
          }
        }
    }
    RetryPolicy(loop(None))
  }

  def forever[F[+_, +_]: Applicative2]: RetryPolicy[F, Any, Long] = {
    def loop(n: Long): RetryFunction[F, Any, Long] = {
      (now, _) => F.pure(ControllerDecision.Repeat(n, now, loop(n + 1L)))
    }
    RetryPolicy(loop(0))
  }

  def recurs[F[+_, +_]: Applicative2](n: Int): RetryPolicy[F, Any, Long] = {
    forever[F].whileOutput(_ < n)
  }

  def spaced[F[+_, +_]: Applicative2](timeInterval: FiniteDuration): RetryPolicy[F, Any, Long] = {
    forever[F].modifyDelay(_ => timeInterval)
  }

  def recursWhile[F[+_, +_]: Applicative2, A](f: A => Boolean): RetryPolicy[F, A, A] = {
    identity[F, A].whileInput(f)
  }

  def fixed[F[+_, +_]: Applicative2](period: FiniteDuration): RetryPolicy[F, Any, Long] = {
    final case class State(startMillis: Long, lastRun: Long)

    val intervalMillis = period.toMillis

    def loop(state: Option[State], n: Long): RetryFunction[F, Any, Long] = {
      (now: ZonedDateTime, _: Any) =>
        F.pure {
          state match {
            case Some(State(startMillis, lastRun)) =>
              val nowMillis = now.toInstant.toEpochMilli
              val runningBehind = nowMillis > lastRun + intervalMillis
              val boundary =
                if (period.length == 0) period
                else FiniteDuration(intervalMillis - (nowMillis - startMillis) % intervalMillis, TimeUnit.MILLISECONDS)

              val sleepTime = if (boundary.length == 0) period else boundary
              val nextRun = if (runningBehind) now else now.plusNanos(sleepTime.toNanos)
              ControllerDecision.Repeat(n + 1L, nextRun, loop(Some(State(startMillis, nextRun.toInstant.toEpochMilli)), n + 1L))
            case None =>
              val nowMillis = now.toInstant.toEpochMilli
              val nextRun = now.plusNanos(period.toNanos)
              ControllerDecision.Repeat(n + 1L, nextRun, loop(Some(State(nowMillis, nextRun.toInstant.toEpochMilli)), n + 1L))
          }
        }
    }

    RetryPolicy(loop(None, 0L))
  }

  def exponential[F[+_, +_]: Applicative2](baseDelay: FiniteDuration): RetryPolicy[F, Any, FiniteDuration] = {
    forever.map(retriesSoFar => multiply(baseDelay, Math.pow(2.0, retriesSoFar.toDouble).toLong)).modifyDelay(d => d)
  }

  def exponentialWithBound[F[+_, +_]: Applicative2](baseDelay: FiniteDuration, maxRetries: Int): RetryPolicy[F, Any, (FiniteDuration, Long)] = {
    exponential(baseDelay) && recurs(maxRetries)
  }

  private[this] def multiply(delay: FiniteDuration, multiplier: Long): FiniteDuration = {
    val base = BigInt(delay.toNanos)
    val result = base * BigInt(multiplier)
    val cappedResult = result min LongMax
    FiniteDuration(cappedResult.toLong, TimeUnit.NANOSECONDS)
  }

  type RetryFunction[+F[+_, +_], -A, +B] = (ZonedDateTime, A) => F[Nothing, ControllerDecision[F, A, B]]
  object RetryFunction {
    def done[F[+_, +_]: Applicative2, A](value: A): RetryFunction[F, Any, A] = {
      (_, _) => F.pure(ControllerDecision.Stop(value))
    }
  }

  sealed trait ControllerDecision[+F[+_, +_], -A, +B]
  object ControllerDecision {
    final case class Repeat[F[+_, +_], -A, +B](out: B, interval: ZonedDateTime, action: RetryFunction[F, A, B]) extends ControllerDecision[F, A, B]
    final case class Stop[+B](out: B) extends ControllerDecision[Nothing, Any, B]
  }
}
