package izumi.fundamentals.bio.test

import izumi.functional.bio.impl.SchedulerZio
import izumi.functional.bio.retry.RetryPolicy.{ControllerDecision, RetryFunction}
import izumi.functional.bio.retry.{RetryPolicy, Scheduler2, toFiniteDuration, toZonedDateTime}
import izumi.functional.bio.{F, Monad2, Primitives2, UnsafeRun2}
import org.scalatest.wordspec.AnyWordSpec
import zio.internal.Platform

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

class SchedulerTest extends AnyWordSpec {
  //private val monixRunner = UnsafeRun2.createMonixBIO(TestScheduler(), bio.IO.defaultOptions)
  private val zioRunner = UnsafeRun2.createZIO(Platform.default)

  // TODO: monix requires timer instance which apparently I should make by myself since I cannot instantiate a default one?
  //private val monixScheduler = new SchedulerMonix(cats.effect.Timer)
  private val zioTestClock = zio.Has(zio.clock.Clock.Service.live)
  private val zioScheduler = new SchedulerZio(zioTestClock)

  "Scheduler" should {

    "recur N times" in {
      val res = zioRunner.unsafeRun(simpleCounter[zio.IO, Long](zioScheduler)(RetryPolicy.recurs[zio.IO](3)))
      assert(res == 4)
    }

    "recur while predicate is true" in {
      val res = zioRunner.unsafeRun(simpleCounter[zio.IO, Int](zioScheduler)(RetryPolicy.recursWhile[zio.IO, Int](_ < 3)))
      assert(res == 3)
    }

    "execute effect with a given period" in {
      val list = zioRunner.unsafeRun(zioTestTimedScheduler(zio.IO.unit)(RetryPolicy.spaced(200.millis), 3))
      assert(list == Vector.fill(3)(200.millis))
    }

    // This one seems weird a bit, but it's the best simple test case I could come up with at the moment
    // Since it took some time to run effect plus execute repeat logic, delays could be slightly less than expected.
    "execute effect within a time window" in {
      val sleeps =
        zioRunner.unsafeRun(zioTestTimedScheduler(zio.ZIO.sleep(java.time.Duration.of(1, ChronoUnit.SECONDS)).provide(zioTestClock))(RetryPolicy.fixed(2.seconds), 4))

      assert(sleeps.head == 2.seconds)
      assert(sleeps.tail.forall(_ <= 1.second))
    }

    "have correct exponential backoff policy" in {

      val baseDelay = 100
      val policy = RetryPolicy.exponential[zio.IO](baseDelay.millis)

      // NOTE: deep recursion here will blow up stack!!!
      def test(rf: RetryFunction[zio.IO, Any, FiniteDuration], now: ZonedDateTime, exp: Int): Unit = {
        val next = zioRunner.unsafeRun(rf(now, ()).map(_.asInstanceOf[ControllerDecision.Repeat[zio.IO, Any, FiniteDuration]]))
        assert(next.out == (baseDelay * math.pow(2.0, exp.toDouble)).toLong.millis)
        if (exp < 4) test(next.action, next.interval, exp + 1) else ()
      }

      test(policy.action, ZonedDateTime.now(), 0)
    }

    "combine different policies properly" in {
      val intersectP = RetryPolicy.recursWhile[zio.IO, Boolean](identity) && RetryPolicy.recurs(4)
      val unionP = RetryPolicy.recursWhile[zio.IO, Boolean](identity) || RetryPolicy.recurs(4)
      val eff = (counter: zio.Ref[Int]) => counter.updateAndGet(_ + 1).map(_ < 3)

      val test = for {
        counter1 <- zio.Ref.make(0)
        counter2 <- zio.Ref.make(0)

        _ <- zioScheduler.repeat(eff(counter1))(intersectP)
        res1 <- counter1.get
        _ = assert(res1 == 3)

        _ <- zioScheduler.repeat(eff(counter2))(unionP)
        res2 <- counter2.get
        _ = assert(res2 == 5)

        np1 = RetryPolicy.spaced[zio.IO](300.millis) && RetryPolicy.spaced[zio.IO](200.millis)
        delays1 <- zioTestTimedScheduler(zio.IO.unit)(np1, 1)
        _ = assert(delays1 == Vector(300.millis))

        np2 = RetryPolicy.spaced[zio.IO](300.millis) || RetryPolicy.spaced[zio.IO](200.millis)
        delays2 <- zioTestTimedScheduler(zio.IO.unit)(np2, 1)
        _ = assert(delays2 == Vector(200.millis))
      } yield ()

      zioRunner.unsafeRun(test)
    }

    "retry effect after fail" in {
      def test(maxRetries: Int, expected: Int) = {
        val eff = (counter: zio.Ref[Int]) => counter.updateAndGet(_ + 1).flatMap(v => if (v < 3) zio.IO.fail(new Throwable("Crap!")) else zio.IO.unit)
        for {
          counter <- zio.Ref.make(0)
          _ <- zioScheduler.retryOrElse(eff(counter))(RetryPolicy.recurs(maxRetries))(_ => counter.set(-1))
          res <- counter.get
          _ = assert(res == expected)
        } yield ()
      }

      val testProgram = for {
        _ <- test(2, 3)
        _ <- test(1, -1)
      } yield ()

      zioRunner.unsafeRun(testProgram)
    }

    "should fail immediately if fail occurs during repeat" in {
      var isSucceed = false
      val eff = (counter: zio.Ref[Int]) => counter.updateAndGet(_ + 1).flatMap(v => if (v < 2) zio.IO.fail(new Throwable("Crap!")) else zio.IO.unit)
      val testProgram = for {
        counter <- zio.Ref.make(0)
        _ <- zioScheduler.repeat(eff(counter))(RetryPolicy.recurs(10)).catchAll(_ => zio.IO { isSucceed = true })
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
        if (iter <= 0) F.pure(acc)
        else {
          (for {
            now <- zio.clock.currentTime(TimeUnit.MILLISECONDS).map(toZonedDateTime)
            dec <- makeDecision(now, in)
            res = dec match {
              case ControllerDecision.Stop(_) => F.pure(acc)
              case ControllerDecision.Repeat(_, interval, next) =>
                val sleep = java.time.Duration.between(now, interval)
                zio.ZIO.sleep(sleep) *> eff *> loop((), next, acc.appended(toFiniteDuration(sleep)), iter - 1)
            }
          } yield res).flatten
        }
      }.provide(zioTestClock)

      loop((), policy.action, Vector.empty[FiniteDuration], n)
    }

    def monixTestTimedRunner[E, B](eff: zio.IO[E, Any])(policy: RetryPolicy[zio.IO, Any, B], n: Int): zio.IO[E, Vector[FiniteDuration]] = {
      ???
    }
  }
}
