package izumi.functional.bio.retry

import izumi.functional.bio.Clock1.ClockAccuracy
import izumi.functional.bio.retry.RetryPolicy.{ControllerDecision, RetryFunction}
import izumi.functional.bio.{Clock2, Error2, F, IO2, Monad2, Primitives2, Ref2, Temporal2, TemporalInstances, UnsafeRun2}
import izumi.distage.testkit.runner.spec.Assertion
import izumi.fundamentals.testkit.AnyWordSpec
import zio.IO

import java.time.{Instant, ZoneOffset, ZonedDateTime}
import scala.annotation.tailrec
import scala.concurrent.duration.*
import scala.jdk.DurationConverters.JavaDurationOps

class SchedulerTest extends AnyWordSpec {

  private val zioClock: Clock2[IO] = Clock2[IO]
  private val zioTemporal: Temporal2[IO] = TemporalInstances.Temporal2Zio
  private val zioScheduler: Scheduler2[IO] = SchedulerInstances.SchedulerFromTemporalAndClock(using zioTemporal, zioClock)
  private val zioRunner: UnsafeRun2[IO] = UnsafeRun2.createZIO[Any]()

  private object implicits {
    implicit val zioClockImplicit: Clock2[IO] = zioClock
    implicit val zioTemporalImplicit: Temporal2[IO] = zioTemporal
    implicit val zioSchedulerImplicit: Scheduler2[IO] = zioScheduler
  }

  def toZonedDateTime(epochMillis: Long): ZonedDateTime = {
    ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC)
  }

  "Scheduler" should {

    "recurs with zero or negative argument repeats effect 0 additional time" in {
      val zio1 = zioRunner.unsafeRun(simpleCounter[IO, Long](zioScheduler)(RetryPolicy.recurs[IO](0)))
      val zio2 = zioRunner.unsafeRun(simpleCounter[IO, Long](zioScheduler)(RetryPolicy.recurs[IO](-5)))

      assert(zio1 == 1)
      assert(zio2 == 1)
    }

    "recur N times" in {
      val res1 = zioRunner.unsafeRun(simpleCounter[IO, Long](zioScheduler)(RetryPolicy.recurs[IO](3)))
      assert(res1 == 4)
    }

    "recur while predicate is true" in {
      val res1 = zioRunner.unsafeRun(simpleCounter[IO, Int](zioScheduler)(RetryPolicy.recursWhile[IO, Int](_ < 3)))
      assert(res1 == 3)
    }

    "execute effect with a given period" in {
      import implicits.*
      val list1 = zioRunner.unsafeRun(testTimedScheduler(zio.ZIO.unit)(RetryPolicy.spaced(200.millis), 3))
      assert(list1 == Vector.fill(3)(200.millis))
    }

    // Since it took some time to run effect plus execute repeat logic, delays could be slightly less than expected.
    "execute effect within a time window" in {
      import implicits.*
      val sleeps1 =
        zioRunner.unsafeRun(testTimedScheduler(zioTemporal.sleep(1.seconds))(RetryPolicy.fixed(2.seconds), 4))

      assert(sleeps1.head == 2.seconds)
      assert(sleeps1.tail.forall(_ <= 1.second))

    }

    "fixed delay" in {
      def policy[F[+_, +_]: Monad2] = RetryPolicy.fixed[F](100.millis) >>> RetryPolicy.elapsed

      val outputs = Vector.newBuilder[FiniteDuration]
      visitPolicy(zioRunner)(policy[IO].action, ZonedDateTime.now(), 4)((next, _, _) => { outputs += next.out; () })
      assert(outputs.result().toList == List(0, 1, 2, 3).map(i => (i * 100).millis))
    }

    "execute spaced" in {
      import implicits.*
      val sleeps1 =
        zioRunner.unsafeRun(testTimedScheduler(zioTemporal.sleep(1.seconds))(RetryPolicy.spaced(2.seconds), 4))

      assert(sleeps1.forall(_ == 2.second))
    }

    "compute exponential backoff intervals correctly" in {
      val baseDelay = 100
      val policy1 = RetryPolicy.exponential[IO](baseDelay.millis)

      visitPolicy(zioRunner)(policy1.action, ZonedDateTime.now(), 4) {
        (next, _, index) =>
          assert(next.out == (baseDelay * math.pow(2.0, index.toDouble)).toLong.millis)
          ()
      }
    }

    "compute fixed intervals correctly" in {
      val policy1 = RetryPolicy.fixed[IO](100.millis)

      val intervals = Vector.newBuilder[Long]
      visitPolicy(zioRunner)(policy1.action, ZonedDateTime.now(), 5) {
        (next, now, _) =>
          intervals += next.interval.toInstant.toEpochMilli - now.toInstant.toEpochMilli
          ()
      }
      assert(intervals.result().forall(_ == 100))
    }

    "combine different policies properly" in {
      import implicits.*
      val intersectPZio = RetryPolicy.recursWhile[IO, Boolean](identity) && RetryPolicy.recurs(4)
      val unionPZio = RetryPolicy.recursWhile[IO, Boolean](identity) || RetryPolicy.recurs(4)
      val effZio = (counter: zio.Ref[Int]) => counter.updateAndGet(_ + 1).map(_ < 3)

      val zioTest = for {
        counter1 <- zio.Ref.make(0)
        counter2 <- zio.Ref.make(0)

        _ <- zioScheduler.repeat(effZio(counter1))(intersectPZio)
        res1 <- counter1.get
        _ = assert(res1 == 3)

        _ <- zioScheduler.repeat(effZio(counter2))(unionPZio)
        res2 <- counter2.get
        _ = assert(res2 == 5)

        np1 = RetryPolicy.spaced[IO](300.millis) && RetryPolicy.spaced[IO](200.millis)
        delays1 <- testTimedScheduler(zio.ZIO.unit)(np1, 1)
        _ = assert(delays1 == Vector(300.millis))

        np2 = RetryPolicy.spaced[IO](300.millis) || RetryPolicy.spaced[IO](200.millis)
        delays2 <- testTimedScheduler(zio.ZIO.unit)(np2, 1)
        _ = assert(delays2 == Vector(200.millis))
      } yield ()

      zioRunner.unsafeRun(zioTest)
    }

    "fail if no more retries left" in {

      def test[F[+_, +_]: IO2](runner: UnsafeRun2[F], scheduler: Scheduler2[F])(policy: RetryPolicy[F, Any, Any]): Assertion = {
        var isSucceed = false
        val test = scheduler.retry(F.fail(new RuntimeException("Crap!")))(policy).catchAll {
          _ =>
            F.sync {
              isSucceed = true
            }
        }
        runner.unsafeRun(test)
        assert(isSucceed)
      }

      test(zioRunner, zioScheduler)(RetryPolicy.recurs(2))
    }

    "retryOrElse more than once both with Scheduler and Temporal" in {
      def test[F[+_, +_]: Temporal2: Primitives2: Scheduler2](): F[String, (Int, Int)] = {
        for {
          schedulerRetries <- for {
            schedulerCounter <- F.mkRef(0)
            _ <- F.retryOrElse {
              for {
                counter <- schedulerCounter.update(_ + 1)
                _ <- F.when(counter < 10)(F.fail("error"))
              } yield ()
            }(RetryPolicy.forever)(_ => F.fail("unreachable"))
            count <- schedulerCounter.get
          } yield count

          temporalRetries <- for {
            temporalCounter <- F.mkRef(0)
            _ <- F.retryOrElseUntil {
              for {
                counter <- temporalCounter.update(_ + 1)
                _ <- F.when(counter < 10)(F.fail("error"))
              } yield ()
            }(1.minute, _ => F.fail("unreachable"))
            count <- temporalCounter.get
          } yield count

        } yield (schedulerRetries, temporalRetries)
      }

      import implicits.*

      val zioRetries = zioRunner.unsafeRun(test[IO]())
      assert(zioRetries == ((10, 10)))
    }

    "retry effect after fail/get fallback value if no more retries left" in {
      def test[F[+_, +_]: Error2: Primitives2: Scheduler2](maxRetries: Int, expected: Int): F[Nothing, Unit] = {
        val eff = (counter: Ref2[F, Int]) => counter.update(_ + 1).flatMap(v => if (v < 3) F.fail(new RuntimeException("Crap!")) else F.unit)
        for {
          counter <- F.mkRef(0)
          _ <- F.retryOrElse(eff(counter))(RetryPolicy.recurs(maxRetries))(_ => counter.set(-1))
          res <- counter.get
          _ = assert(res == expected)
        } yield ()
      }

      import implicits.*

      zioRunner.unsafeRun {
        for {
          _ <- test[IO](2, 3)
          _ <- test[IO](1, -1)
        } yield ()
      }

    }

    "fail immediately if fail occurs during repeat" in {
      def testZio() = {
        var isSucceed = false
        val eff = (counter: zio.Ref[Int]) => counter.updateAndGet(_ + 1).flatMap(v => if (v < 2) zio.ZIO.fail(new RuntimeException("Crap!")) else zio.ZIO.unit)
        val testProgram = for {
          counter <- zio.Ref.make(0)
          _ <- zioScheduler
            .repeat(eff(counter))(RetryPolicy.recurs(10)).catchAll(
              _ =>
                zio.ZIO.succeed {
                  isSucceed = true
                }
            )
          _ = assert(isSucceed)
        } yield ()
        zioRunner.unsafeRun(testProgram)
      }

      testZio()
    }

    "run the specified finalizer as soon as the schedule is complete" in {
      val testProgram = for {
        p <- zio.Promise.make[Throwable, Unit]
        _ <- zioScheduler.retryOrElse(zio.ZIO.fail(new RuntimeException("Crap!")))(RetryPolicy.recurs(2))(_ => zio.ZIO.unit).ensuring(p.succeed(()))
        finalizerV <- p.poll
        _ = assert(finalizerV.isDefined)
      } yield ()

      zioRunner.unsafeRun(testProgram)
    }

    def simpleCounter[F[+_, +_]: Monad2: Primitives2, B](sc: Scheduler2[F])(policy: RetryPolicy[F, Int, B]): F[Nothing, Int] = {
      for {
        counter <- F.mkRef(0)
        res <- sc.repeat(counter.update(_ + 1))(policy)
      } yield res
    }

    def testTimedScheduler[F[+_, +_]: Temporal2: Clock2, E, B](eff: F[E, Any])(policy: RetryPolicy[F, Any, B], n: Int): F[E, Vector[FiniteDuration]] = {
      def loop(in: Any, makeDecision: RetryFunction[F, Any, B], acc: Vector[FiniteDuration], iter: Int): F[E, Vector[FiniteDuration]] = {
        if (iter <= 0) F.pure(acc)
        else {
          (for {
            now <- F.clock.nowZoned(ClockAccuracy.MILLIS)
            dec <- makeDecision(now, in)
            res = dec match {
              case _: ControllerDecision.Stop[B] @unchecked => F.pure(acc)
              case repeat: ControllerDecision.Repeat[F, Any, B] @unchecked =>
                val next = repeat.action
                val sleepTime = java.time.Duration.between(now, repeat.interval).toScala
                F.sleep(sleepTime) *> eff *> loop((), next, acc :+ sleepTime, iter - 1)
            }
          } yield res).flatten
        }
      }

      loop((), policy.action, Vector.empty[FiniteDuration], n)
    }

  }

  private def visitPolicy[F[+_, +_], B](
    runner: UnsafeRun2[F]
  )(action: RetryFunction[F, Any, B],
    now: ZonedDateTime,
    attempts: Int,
  )(check: (ControllerDecision.Repeat[F, Any, B], ZonedDateTime, Int) => Unit
  ): Unit = {
    @tailrec
    def loop(current: RetryFunction[F, Any, B], time: ZonedDateTime, index: Int): Unit = {
      if (index < attempts) {
        val next = runner.unsafeRun(current(time, ())) match {
          case repeat: ControllerDecision.Repeat[F, Any, B] @unchecked => repeat
          case stop: ControllerDecision.Stop[B] @unchecked => fail(s"unexpected result $stop")
        }
        check(next, time, index)
        loop(next.action, next.interval, index + 1)
      }
    }
    loop(action, now, 0)
  }

}
