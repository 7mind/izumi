package izumi.functional.bio.laws.env

import cats.Eq
import cats.effect.kernel.Outcome
import cats.effect.testkit.TestInstances
import cats.kernel.Order
import izumi.functional.bio.Exit.{CatsExit, ZIOExit}
import izumi.functional.bio.data.Morphism1
import izumi.functional.bio.{Clock1, Clock2, IO2}
import org.scalacheck.{Arbitrary, Cogen, Prop}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.Duration
import zio.internal.{Executor, Tracing}
import zio.{IO, Task, UIO, ZIO}

import java.time.*
import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.concurrent.duration.Duration.Infinite
import scala.concurrent.duration.FiniteDuration

trait ZIOTestEnv extends TestInstances with EqThrowable {

  implicit def eqTask[A: Eq](implicit tc: Ticker): Eq[Task[A]] =
    Eq.by(io => toIO(io.either.map(_.map(_ => ()).left.map(_ => ()))))

  implicit def orderTask(implicit tc: Ticker): Order[Task[FiniteDuration]] =
    Order.by(io => toIO(io))

  implicit def execTask(implicit ticker: Ticker): Task[Boolean] => Prop = {
    toIO(_)
  }

  implicit def clock2(implicit ticker: Ticker): Clock2[IO] = {
    new Clock2[IO] {
      override def epoch: IO[Nothing, Long] = UIO(ticker.ctx.now().toMillis)
      override def now(accuracy: Clock1.ClockAccuracy): IO[Nothing, ZonedDateTime] = UIO(
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(ticker.ctx.now().toMillis), ZoneOffset.UTC)
      )
      override def nowLocal(accuracy: Clock1.ClockAccuracy): IO[Nothing, LocalDateTime] = UIO(
        LocalDateTime.ofInstant(Instant.ofEpochMilli(ticker.ctx.now().toMillis), ZoneOffset.UTC)
      )
      override def nowOffset(accuracy: Clock1.ClockAccuracy): IO[Nothing, OffsetDateTime] = UIO(
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(ticker.ctx.now().toMillis), ZoneOffset.UTC)
      )
      override def monotonicNano: IO[Nothing, Long] = UIO(ticker.ctx.now().toNanos)
    }
  }

  implicit def zioBlocking(implicit ticker: Ticker): Blocking = {
    zio.Has(new Blocking.Service {
      override def blockingExecutor: Executor = Executor.fromExecutionContext(Int.MaxValue)(ticker.ctx)
    })
  }

  implicit def zioClock(implicit ticker: Ticker): Clock = {
    zio.Has(new Clock.Service {
      override def currentTime(unit: TimeUnit): UIO[Long] = UIO(ticker.ctx.now().toUnit(unit).toLong)
      override def currentDateTime: IO[DateTimeException, OffsetDateTime] = UIO(OffsetDateTime.ofInstant(Instant.ofEpochMilli(ticker.ctx.now().toMillis), ZoneOffset.UTC))
      override def nanoTime: UIO[Long] = UIO(ticker.ctx.now().toNanos)
      override def sleep(duration: Duration): UIO[Unit] = {
        import zio.duration.*
        duration.asScala match {
          case f: FiniteDuration =>
            UIO.effectAsyncInterrupt {
              k =>
                val canceler = ticker.ctx.schedule(f, () => k(ZIO.unit))
                Left(UIO(canceler()))
            }
          case _: Infinite =>
            ZIO.never
        }
      }
    })
  }

  implicit def cogenTask[A: Cogen](implicit ticker: Ticker): Cogen[IO[Throwable, A]] = {
    Cogen[cats.effect.IO[Outcome[cats.effect.IO, Throwable, A]]].contramap {
      io: IO[Throwable, A] =>
        toIO(io.sandboxExit.map(exit => CatsExit.exitToOutcomeThrowable[IO, A](exit)))
          .map(_.mapK(Morphism1[IO[Throwable, +_], cats.effect.IO](toIO(_)).toCats))
    }
  }

  // FIXME: ZIO cannot pass laws related to `MonadCancel#cancel` when using a fair generator
  //  since it does not properly implement it
//  implicit def arbTask[A: Arbitrary: Cogen](implicit ticker: Ticker): Arbitrary[Task[A]] = Arbitrary {
//    arbitraryIO[A].arbitrary.map(liftIO(_))
//  }
//  def liftIO[A](io: cats.effect.IO[A])(implicit ticker: Ticker): zio.Task[A] = {
//    ZIO.effectAsyncInterrupt {
//      k =>
//        val (result, cancel) = io.unsafeToFutureCancelable()
//        k(ZIO.fromFuture(_ => result))
//        Left(ZIO.fromFuture(_ => cancel()).orDie)
//    }
//  }
  implicit def arbTask[A](implicit arb: Arbitrary[A]): Arbitrary[Task[A]] = Arbitrary {
    Arbitrary.arbBool.arbitrary.flatMap {
      if (_) arb.arbitrary.map(IO2[IO].pure(_))
      else Arbitrary.arbThrowable.arbitrary.map(IO2[IO].fail(_))
    }
  }

  def toIO[A](io: IO[Throwable, A])(implicit ticker: Ticker): cats.effect.IO[A] = {
    val F = cats.effect.IO.asyncForIO
    cats.effect.IO.uncancelable {
      poll =>
        val runtime = zio
          .Runtime((), zio.internal.Platform.fromExecutionContext(ticker.ctx))
          .withTracing(Tracing.disabled)
          .withReportFailure(_ => ())
        F.delay(
          runtime.unsafeRunToFuture(io.run)
        ).flatMap {
            future =>
              poll(
                F.onCancel(
                  F.fromFuture(F.pure[Future[zio.Exit[Throwable, A]]](future)).flatMap {
                    exit =>
                      val outcome = CatsExit.toOutcomeThrowable(F.pure(_: A), ZIOExit.toExit(exit))
                      outcome match {
                        case Outcome.Succeeded(fa) =>
//                          println(s"succ $fa ${fa.getClass}")
                          fa
                        case Outcome.Errored(e) =>
//                          println(s"err $e ${e.getClass}")
                          F.raiseError(e)
                        case Outcome.Canceled() =>
//                          println("canceled")
                          def go: cats.effect.IO[A] = F.canceled >> go
                          go
                      }
                  },
                  F.fromFuture(F.delay(future.cancel())).void,
                )
              )
          }
    }
  }
}
