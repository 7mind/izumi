package izumi.functional.bio.laws.env

import cats.Eq
import cats.effect.kernel.Outcome
import cats.effect.testkit.TestInstances
import cats.kernel.Order
import izumi.functional.bio.Exit.{CatsExit, ZIOExit}
import izumi.functional.bio.data.Morphism1
import izumi.functional.bio.{Clock1, Clock2}
import org.scalacheck.{Arbitrary, Cogen, Prop}
import zio.{Clock, Duration, Executor, IO, Runtime, Scheduler, Task, Trace, UIO, Unsafe, ZIO, ZLayer}

import java.time
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration.Duration.Infinite
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext}

trait ZIOTestEnv extends TestInstances with EqThrowable {

  // workaround for laws `evalOn local pure` & `executionContext commutativity`
  // (ZIO cannot implement them at all due to `.executor.asEC` losing the original executionContext)
  given eqForExecutionContext: Eq[ExecutionContext] =
    Eq.allEqual

  override given eqIOA[A: Eq](using ticker: Ticker): Eq[cats.effect.IO[A]] = {
    (ioa, iob) =>
      {
        val a = this.unsafeRun(ioa)
        val b = this.unsafeRun(iob)
        Eq[Outcome[Option, Throwable, A]].eqv(a, b) || {
          System.err.println(s"not equal a=$a b=$b")
          false
        }
      }
  }

  given eqTask[A: Eq](using tc: Ticker): Eq[Task[A]] =
    Eq.by(io => toIO(io))

  given orderTask(using tc: Ticker): Order[Task[FiniteDuration]] =
    Order.by(io => toIO(io))

  given execTask(using ticker: Ticker): (Task[Boolean] => Prop) = {
    toIO(_)
  }

  given clock2(using ticker: Ticker): Clock2[IO] = {
    new Clock2[IO] {
      override def epoch: IO[Nothing, Long] = ZIO.succeed(ticker.ctx.now().toMillis)
      override def now(accuracy: Clock1.ClockAccuracy): IO[Nothing, ZonedDateTime] = nowZoned(accuracy)
      override def nowLocal(accuracy: Clock1.ClockAccuracy, zone: ZoneId): IO[Nothing, LocalDateTime] = ZIO.succeed(
        LocalDateTime.ofInstant(Instant.ofEpochMilli(ticker.ctx.now().toMillis), zone)
      )
      override def nowOffset(accuracy: Clock1.ClockAccuracy, zone: ZoneId): IO[Nothing, OffsetDateTime] = ZIO.succeed(
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(ticker.ctx.now().toMillis), zone)
      )
      override def monotonicNano: IO[Nothing, Long] = ZIO.succeed(ticker.ctx.now().toNanos)

      override def nowZoned(accuracy: Clock1.ClockAccuracy, zone: ZoneId): IO[Nothing, ZonedDateTime] = ZIO.succeed(
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(ticker.ctx.now().toMillis), zone)
      )
    }
  }

  def zioClock(using ticker: Ticker): Clock = {
    new Clock {
      override def currentTime(unit: => TimeUnit)(using trace: Trace): UIO[Long] = {
        ZIO.succeed(ticker.ctx.now().toUnit(unit).toLong)
      }
      override def currentDateTime(using trace: Trace): UIO[OffsetDateTime] = {
        ZIO.succeed(OffsetDateTime.ofInstant(Instant.ofEpochMilli(ticker.ctx.now().toMillis), ZoneOffset.UTC))
      }
      override def nanoTime(using trace: Trace): UIO[Long] = {
        ZIO.succeed(ticker.ctx.now().toNanos)
      }
      override def sleep(duration: => Duration)(using trace: Trace): UIO[Unit] = {
        import zio.*
        duration.asScala match {
          case f: FiniteDuration =>
            ZIO.asyncInterrupt {
              k =>
                val canceler = ticker.ctx.schedule(f, () => k(ZIO.unit))
                Left(ZIO.succeed(canceler()))
            }
          case _: Infinite =>
            ZIO.never
        }
      }

      override def currentTime(unit: => ChronoUnit)(using trace: Trace, d: DummyImplicit): UIO[Long] = ???
      override def instant(using trace: Trace): UIO[Instant] = ???
      override def javaClock(using trace: Trace): UIO[time.Clock] = ???
      override def localDateTime(using trace: Trace): UIO[LocalDateTime] = ???
      override def scheduler(using trace: Trace): UIO[Scheduler] = ???
    }
  }

  given cogenTask[A: Cogen](using ticker: Ticker): Cogen[IO[Throwable, A]] = {
    Cogen[cats.effect.IO[Outcome[cats.effect.IO, Throwable, A]]].contramap {
      (io: IO[Throwable, A]) =>
        import izumi.functional.bio.Root.BIOZIO

        toIO(BIOZIO.sandboxExit(io).map(exit => CatsExit.exitToOutcomeThrowable[IO, A](exit)))
          .map(_.mapK(Morphism1[IO[Throwable, +_], cats.effect.IO](toIO(_)).toCats))
    }
  }

  given arbTask[A: Arbitrary: Cogen](using ticker: Ticker): Arbitrary[Task[A]] = Arbitrary {
    arbitraryIO[A].arbitrary.map(liftIO(_))
  }
  def liftIO[A](io: cats.effect.IO[A])(using ticker: Ticker): zio.Task[A] = {
    ZIO.asyncInterrupt {
      k =>
        val (result, cancel) = io.unsafeToFutureCancelable()
        k(ZIO.fromFuture(_ => result))
        Left(ZIO.fromFuture(_ => cancel()).orDie)
    }
  }
//  given arbTask[A](using arb: Arbitrary[A]): Arbitrary[Task[A]] = Arbitrary {
//    Arbitrary.arbBool.arbitrary.flatMap {
//      if (_) arb.arbitrary.map(IO2[IO].pure(_))
//      else Arbitrary.arbThrowable.arbitrary.map(IO2[IO].fail(_))
//    }
//  }

  def toIO[A](io: IO[Throwable, A])(using ticker: Ticker): cats.effect.IO[A] = {
    val F = cats.effect.IO.asyncForIO
    val interrupted = new AtomicBoolean(true)
    F.async[zio.Exit[Throwable, A]] {
      cb =>
        given unsafe: Unsafe = Unsafe.unsafe(identity)
        val runtime = Runtime.unsafe
          .fromLayer {
            Runtime.setExecutor(Executor.fromExecutionContext(ticker.ctx)) >+>
            Runtime.setBlockingExecutor(Executor.fromExecutionContext(ticker.ctx)) >+>
            ZLayer.scoped(ZIO.withClockScoped(zioClock))
          }
        val canceler = runtime.unsafe.runToFuture {
          ZIOExit
            .ZIOSignalOnNoExternalInterruptFailure {
              io
            }(ZIO.succeed(interrupted.set(false)))
            .onExit(exit => ZIO.succeed(cb(Right(exit))))
        }
        val cancelerEffect = cats.effect.IO { val _: zio.Exit[Throwable, A] = Await.result(canceler.cancel(), scala.concurrent.duration.Duration.Inf) }
        F.pure(Some(cancelerEffect))
    }.flatMap {
        exit =>
          val outcome = CatsExit.toOutcomeThrowable(F.pure(_: A), ZIOExit.toExit(exit)(interrupted.get()))
          outcome match {
            case Outcome.Succeeded(fa) =>
              if (loud) println(s"succ $fa ${fa.getClass}")
              fa
            case Outcome.Errored(e) =>
              if (loud) println(s"err $e ${e.getClass}")
              F.raiseError(e)
            case Outcome.Canceled() =>
              if (loud) println("canceled")
              F.canceled.flatMap(_ => F.raiseError(new InterruptedException("_test fiber canceled_")))
          }
      }
  }

  def loud: Boolean = false

}
