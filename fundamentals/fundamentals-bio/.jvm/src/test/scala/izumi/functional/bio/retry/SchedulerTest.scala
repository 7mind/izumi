package izumi.functional.bio.retry

import izumi.functional.bio.__VersionSpecificDurationConvertersCompat.toFiniteDuration
import izumi.functional.bio.impl.SchedulerZio
import izumi.functional.bio.retry.RetryPolicy.{ControllerDecision, RetryFunction}
import izumi.functional.bio.{F, Functor2, IO2, Monad2, Primitives2, UnsafeRun2}
import org.scalatest.wordspec.AnyWordSpec
import zio.internal.Platform

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import scala.annotation.tailrec
import scala.concurrent.duration.*

class SchedulerTest extends AnyWordSpec {
//    private def monixSchedulerFromTimer(implicit timer: cats.effect.kernel.Clock[bio.UIO]) = new SchedulerMonix(timer)
//
//  private val monixRunner = UnsafeRun2.createMonixBIO(Scheduler.global, bio.IO.defaultOptions)
//  private val monixScheduler = monixSchedulerFromTimer

  private val zioTestClock = zio.Has(zio.clock.Clock.Service.live)
  private val zioScheduler = new SchedulerZio(zioTestClock)
  private val zioRunner = UnsafeRun2.createZIO(Platform.default)

  "Scheduler" should {

    "recurs with zero or negative argument repeats effect 0 additional time" in {
      val zio1 = zioRunner.unsafeRun(simpleCounter[zio.IO, Long](zioScheduler)(RetryPolicy.recurs[zio.IO](0)))
      val zio2 = zioRunner.unsafeRun(simpleCounter[zio.IO, Long](zioScheduler)(RetryPolicy.recurs[zio.IO](-5)))

//      val m1 = monixRunner.unsafeRun(simpleCounter[bio.IO, Long](monixScheduler)(RetryPolicy.recurs[bio.IO](0)))
//      val m2 = monixRunner.unsafeRun(simpleCounter[bio.IO, Long](monixScheduler)(RetryPolicy.recurs[bio.IO](-283)))

      assert(zio1 == 1)
      assert(zio2 == 1)
//      assert(m1 == 1)
//      assert(m2 == 1)
    }

    "recur N times" in {
      val res1 = zioRunner.unsafeRun(simpleCounter[zio.IO, Long](zioScheduler)(RetryPolicy.recurs[zio.IO](3)))
//      val res2 = monixRunner.unsafeRun(simpleCounter[bio.IO, Long](monixScheduler)(RetryPolicy.recurs[bio.IO](3)))
      assert(res1 == 4)
//      assert(res2 == 4)
    }

    "recur while predicate is true" in {
      val res1 = zioRunner.unsafeRun(simpleCounter[zio.IO, Int](zioScheduler)(RetryPolicy.recursWhile[zio.IO, Int](_ < 3)))
//      val res2 = monixRunner.unsafeRun(simpleCounter[bio.IO, Int](monixScheduler)(RetryPolicy.recursWhile[bio.IO, Int](_ < 3)))
      assert(res1 == 3)
//      assert(res2 == 3)
    }

    "execute effect with a given period" in {
      val list1 = zioRunner.unsafeRun(zioTestTimedScheduler(zio.IO.unit)(RetryPolicy.spaced(200.millis), 3))
//      val list2 = monixRunner.unsafeRun(monixTestTimedRunner(bio.IO.unit)(RetryPolicy.spaced(200.millis), 3))
      assert(list1 == Vector.fill(3)(200.millis))
//      assert(list2 == Vector.fill(3)(200.millis))
    }

    // This one seems weird a bit, but it's the best simple test case I could come up with at the moment
    // Since it took some time to run effect plus execute repeat logic, delays could be slightly less than expected.
    "execute effect within a time window" in {
      val sleeps1 =
        zioRunner.unsafeRun(zioTestTimedScheduler(zio.ZIO.sleep(java.time.Duration.of(1, ChronoUnit.SECONDS)).provide(zioTestClock))(RetryPolicy.fixed(2.seconds), 4))

//      val sleeps2 =
//        monixRunner.unsafeRun(monixTestTimedRunner(bio.IO.sleep(1.second))(RetryPolicy.fixed(2.seconds), 4))

      assert(sleeps1.head == 2.seconds)
      assert(sleeps1.tail.forall(_ <= 1.second))

//      assert(sleeps2.head == 2.seconds)
//      assert(sleeps2.tail.forall(_ <= 1.second))
    }

    "fixed delay" in {
      def policy[F[+_, +_]: Monad2] = RetryPolicy.fixed[F](100.millis) >>> RetryPolicy.elapsed

      def test[F[+_, +_]: Functor2](runner: UnsafeRun2[F])(rf: RetryFunction[F, Any, FiniteDuration], now: ZonedDateTime, numOfRetries: Int) = {
        val acc = scala.collection.mutable.ArrayBuffer.empty[FiniteDuration]
        @tailrec
        def loop(f: RetryFunction[F, Any, FiniteDuration], time: ZonedDateTime, curr: Int): Unit = {
          if (curr < numOfRetries) {
            val next = runner.unsafeRun(f(time, ()).map(_.asInstanceOf[ControllerDecision.Repeat[F, Any, FiniteDuration]]))
            acc.append(next.out)
            loop(next.action, next.interval, curr + 1)
          } else ()
        }

        loop(rf, now, 0)
        acc.toList
      }

      val expected = List(0, 1, 2, 3).map(i => (i * 100).millis)
      val zioTest = test(zioRunner)(policy[zio.IO].action, ZonedDateTime.now(), 4)
      assert(zioTest == expected)

//      val monixTest = test(monixRunner)(policy[bio.IO].action, ZonedDateTime.now(), 4)
//      assert(monixTest == expected)
    }

    "execute spaced" in {
      val sleeps1 =
        zioRunner.unsafeRun(zioTestTimedScheduler(zio.ZIO.sleep(java.time.Duration.of(1, ChronoUnit.SECONDS)).provide(zioTestClock))(RetryPolicy.spaced(2.seconds), 4))

//      val sleeps2 =
//        monixRunner.unsafeRun(monixTestTimedRunner(bio.IO.sleep(1.second))(RetryPolicy.spaced(2.seconds), 4))

      assert(sleeps1.forall(_ == 2.second))
//      assert(sleeps2.forall(_ == 2.second))
    }

    "compute exponential backoff intervals correctly" in {
      val baseDelay = 100
      val policy1 = RetryPolicy.exponential[zio.IO](baseDelay.millis)
//      val policy2 = RetryPolicy.exponential[bio.IO](baseDelay.millis)

      def test[F[+_, +_]: Functor2](runner: UnsafeRun2[F])(rf: RetryFunction[F, Any, FiniteDuration], now: ZonedDateTime, numOfRetries: Int): Unit = {
        @tailrec
        def loop(f: RetryFunction[F, Any, FiniteDuration], time: ZonedDateTime, curr: Int): Unit = {
          if (curr < numOfRetries) {
            val next = runner.unsafeRun(f(time, ()).map(_.asInstanceOf[ControllerDecision.Repeat[F, Any, FiniteDuration]]))
            assert(next.out == (baseDelay * math.pow(2.0, curr.toDouble)).toLong.millis)
            loop(next.action, next.interval, curr + 1)
          } else ()
        }

        loop(rf, now, 0)
      }

      test(zioRunner)(policy1.action, ZonedDateTime.now(), 4)
//      test(monixRunner)(policy2.action, ZonedDateTime.now(), 4)
    }

//    "compute fixed intervals correctly" in {
//      val policy = RetryPolicy.fixed[bio.IO](100.millis)
//      val acc = scala.collection.mutable.ArrayBuffer.empty[Long]
//
//      @tailrec
//      def test(rf: RetryFunction[bio.IO, Any, Long], now: ZonedDateTime, exp: Int): Unit = {
//        val next = monixRunner.unsafeRun(rf(now, ()).map(_.asInstanceOf[ControllerDecision.Repeat[bio.IO, Any, Long]]))
//        acc.append(next.interval.toInstant.toEpochMilli - now.toInstant.toEpochMilli)
//        if (exp < 4) test(next.action, next.interval, exp + 1) else ()
//      }
//
//      test(policy.action, ZonedDateTime.now(), 0)
//      assert(acc.forall(_ == 100))
//    }

    "combine different policies properly" in {
      val intersectPZio = RetryPolicy.recursWhile[zio.IO, Boolean](identity) && RetryPolicy.recurs(4)
      val unionPZio = RetryPolicy.recursWhile[zio.IO, Boolean](identity) || RetryPolicy.recurs(4)
      val effZio = (counter: zio.Ref[Int]) => counter.updateAndGet(_ + 1).map(_ < 3)

//      val intersectPMonix = RetryPolicy.recursWhile[bio.IO, Boolean](identity) && RetryPolicy.recurs(4)
//      val unionPMonix = RetryPolicy.recursWhile[bio.IO, Boolean](identity) || RetryPolicy.recurs(4)
//      val effMonix = (counter: cats.effect.concurrent.Ref[bio.Task, Int]) => counter.updateAndGet(_ + 1).map(_ < 3)

      val zioTest = for {
        counter1 <- zio.Ref.make(0)
        counter2 <- zio.Ref.make(0)

        _ <- zioScheduler.repeat(effZio(counter1))(intersectPZio)
        res1 <- counter1.get
        _ = assert(res1 == 3)

        _ <- zioScheduler.repeat(effZio(counter2))(unionPZio)
        res2 <- counter2.get
        _ = assert(res2 == 5)

        np1 = RetryPolicy.spaced[zio.IO](300.millis) && RetryPolicy.spaced[zio.IO](200.millis)
        delays1 <- zioTestTimedScheduler(zio.IO.unit)(np1, 1)
        _ = assert(delays1 == Vector(300.millis))

        np2 = RetryPolicy.spaced[zio.IO](300.millis) || RetryPolicy.spaced[zio.IO](200.millis)
        delays2 <- zioTestTimedScheduler(zio.IO.unit)(np2, 1)
        _ = assert(delays2 == Vector(200.millis))
      } yield ()

//      val monixTest = for {
//        counter1 <- cats.effect.concurrent.Ref.of[bio.Task, Int](0)
//        counter2 <- cats.effect.concurrent.Ref.of[bio.Task, Int](0)
//
//        _ <- monixScheduler.repeat(effMonix(counter1))(intersectPMonix)
//        res1 <- counter1.get
//        _ = assert(res1 == 3)
//
//        _ <- monixScheduler.repeat(effMonix(counter2))(unionPMonix)
//        res2 <- counter2.get
//        _ = assert(res2 == 5)
//
//        np1 = RetryPolicy.spaced[bio.IO](300.millis) && RetryPolicy.spaced[bio.IO](200.millis)
//        delays1 <- monixTestTimedRunner(bio.IO.unit)(np1, 1)
//        _ = assert(delays1 == Vector(300.millis))
//
//        np2 = RetryPolicy.spaced[bio.IO](300.millis) || RetryPolicy.spaced[bio.IO](200.millis)
//        delays2 <- monixTestTimedRunner(bio.IO.unit)(np2, 1)
//        _ = assert(delays2 == Vector(200.millis))
//      } yield ()

      zioRunner.unsafeRun(zioTest)
//      monixRunner.unsafeRun(monixTest)
    }

    "fail if no more retries left" in {

      def test[F[+_, +_]: IO2](runner: UnsafeRun2[F], scheduler: Scheduler2[F])(policy: RetryPolicy[F, Any, Any]) = {
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
//      test(monixRunner, monixScheduler)(RetryPolicy.recurs(2))
    }

    "retry effect after fail/get fallback value if no more retries left" in {
      def testZio(maxRetries: Int, expected: Int) = {
        val eff = (counter: zio.Ref[Int]) => counter.updateAndGet(_ + 1).flatMap(v => if (v < 3) zio.IO.fail(new RuntimeException("Crap!")) else zio.IO.unit)
        for {
          counter <- zio.Ref.make(0)
          _ <- zioScheduler.retryOrElse(eff(counter))(RetryPolicy.recurs(maxRetries))(_ => counter.set(-1))
          res <- counter.get
          _ = assert(res == expected)
        } yield ()
      }

//      def testMonix(maxRetries: Int, expected: Int) = {
//        val eff = (counter: cats.effect.concurrent.Ref[bio.Task, Int]) =>
//          counter.updateAndGet(_ + 1).flatMap(v => if (v < 3) bio.IO.raiseError(new RuntimeException("Crap!")) else bio.IO.unit)
//        for {
//          counter <- cats.effect.concurrent.Ref.of[bio.Task, Int](0)
//          _ <- monixScheduler.retryOrElse(eff(counter))(RetryPolicy.recurs(maxRetries))(_ => counter.set(-1))
//          res <- counter.get
//          _ = assert(res == expected)
//        } yield ()
//      }

      zioRunner.unsafeRun {
        for {
          _ <- testZio(2, 3)
          _ <- testZio(1, -1)
        } yield ()
      }

//      monixRunner.unsafeRun {
//        for {
//          _ <- testMonix(2, 3)
//          _ <- testMonix(1, -1)
//        } yield ()
//      }
    }

    "fail immediately if fail occurs during repeat" in {
      def testZio() = {
        var isSucceed = false
        val eff = (counter: zio.Ref[Int]) => counter.updateAndGet(_ + 1).flatMap(v => if (v < 2) zio.IO.fail(new RuntimeException("Crap!")) else zio.IO.unit)
        val testProgram = for {
          counter <- zio.Ref.make(0)
          _ <- zioScheduler
            .repeat(eff(counter))(RetryPolicy.recurs(10)).catchAll(
              _ =>
                zio.IO {
                  isSucceed = true
                }
            )
          _ = assert(isSucceed)
        } yield ()
        zioRunner.unsafeRun(testProgram)
      }

//      def testMonix() = {
//        var isSucceed = false
//        val eff = (counter: cats.effect.concurrent.Ref[bio.Task, Int]) =>
//          counter.updateAndGet(_ + 1).flatMap(v => if (v < 2) bio.IO.raiseError(new RuntimeException("Crap!")) else bio.IO.unit)
//        val testProgram = for {
//          counter <- cats.effect.concurrent.Ref.of[bio.Task, Int](0)
//          _ <- monixScheduler
//            .repeat(eff(counter))(RetryPolicy.recurs(10)).onErrorHandleWith(
//              _ =>
//                bio.IO {
//                  isSucceed = true
//                }
//            )
//          _ = assert(isSucceed)
//        } yield ()
//        monixRunner.unsafeRun(testProgram)
//      }

      testZio()
//      testMonix()
    }

    "run the specified finalizer as soon as the schedule is complete" in {
      val testProgram = for {
        p <- zio.Promise.make[Throwable, Unit]
        _ <- zioScheduler.retryOrElse(zio.IO.fail(new RuntimeException("Crap!")))(RetryPolicy.recurs(2))(_ => zio.IO.unit).ensuring(p.succeed(()))
        finalizerV <- p.poll
        _ = assert(finalizerV.isDefined)
      } yield ()

      zioRunner.unsafeRun(testProgram)
    }

    def simpleCounter[F[+_, +_]: Monad2: Primitives2, B](sc: Scheduler2[F])(policy: RetryPolicy[F, Int, B]) = {
      for {
        counter <- F.mkRef(0)
        res <- sc.repeat(counter.update(_ + 1))(policy)
      } yield res
    }

    def zioTestTimedScheduler[E, B](eff: zio.IO[E, Any])(policy: RetryPolicy[zio.IO, Any, B], n: Int): zio.IO[E, Vector[FiniteDuration]] = {
      def loop(in: Any, makeDecision: RetryFunction[zio.IO, Any, B], acc: Vector[FiniteDuration], iter: Int): zio.IO[E, Vector[FiniteDuration]] = {
        if (iter <= 0) zio.IO.succeed(acc)
        else {
          (for {
            now <- zio.clock.currentTime(TimeUnit.MILLISECONDS).map(toZonedDateTime)
            dec <- makeDecision(now, in)
            res = dec match {
              case ControllerDecision.Stop(_) => zio.IO.succeed(acc)
              case ControllerDecision.Repeat(_, interval, next) =>
                val sleep = java.time.Duration.between(now, interval)
                zio.ZIO.sleep(sleep) *> eff *> loop((), next, acc :+ toFiniteDuration(sleep), iter - 1)
            }
          } yield res).flatten
        }
      }.provide(zioTestClock)

      loop((), policy.action, Vector.empty[FiniteDuration], n)
    }

//    def monixTestTimedRunner[E, B](
//      eff: bio.IO[E, Any]
//    )(policy: RetryPolicy[bio.IO, Any, B],
//      n: Int,
//    )(implicit timer: cats.effect.kernel.Clock[bio.UIO]
//    ): bio.IO[E, Vector[FiniteDuration]] = {
//      def loop(in: Any, makeDecision: RetryFunction[bio.IO, Any, B], acc: Vector[FiniteDuration], iter: Int): bio.IO[E, Vector[FiniteDuration]] = {
//        if (iter <= 0) bio.IO.pure(acc)
//        else {
//          (for {
//            now <- timer.clock.monotonic(TimeUnit.MILLISECONDS).map(toZonedDateTime)
//            dec <- makeDecision(now, in)
//            res = dec match {
//              case ControllerDecision.Stop(_) => bio.IO.pure(acc)
//              case ControllerDecision.Repeat(_, interval, next) =>
//                val sleep = toFiniteDuration(java.time.Duration.between(now, interval))
//                bio.IO.sleep(sleep) *> eff *> loop((), next, acc :+ sleep, iter - 1)
//            }
//          } yield res).flatten
//        }
//      }
//
//      loop((), policy.action, Vector.empty[FiniteDuration], n)
//    }
  }
}
