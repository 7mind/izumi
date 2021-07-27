//package izumi.fundamentals.bio.test
//
//import izumi.functional.bio.retry.RetryPolicy
//import izumi.functional.bio.{Clock2, F, Ref2, Temporal2}
//import zio.IO
//
//import java.time.ZonedDateTime
//import scala.concurrent.duration._
//
//class SchedulerTest extends Spec2[IO] with AssertZIO {
//  "Scheduler" should {
//
//    "recur N times" in scopeIO {
//      ctx =>
//
//        for {
//          res <- simpleCounter(RetryPolicy.recurs(3))
//          _   <- assertIO(res == 4)
//        } yield ()
//    }
//
//    "recur while predicate is true" in scopeIO {
//      ctx =>
//
//        for {
//          res <- simpleCounter(RetryPolicy.recursWhile[F, Int](_ < 3))
//          _   <- assertIO(res == 3)
//        } yield ()
//    }
//
//    "execute effect with a given period" in scopeIO {
//      ctx =>
//
//        for {
//          list <- runner(F.unit)(RetryPolicy.spaced(200.millis), 3)
//          _    <- assertIO(list == Vector.fill(3)(200.millis))
//        } yield ()
//    }
//
//    // This one seems weird a bit, but it's the best simple test case I could come up with at the moment
//    // Since it took some time to run effect plus execute repeat logic, delays could be slightly less than expected.
//    "execute effect within a time window" in scopeIO {
//      ctx =>
//
//        for {
//          sleeps <- runner(T.sleep(1.second))(RetryPolicy.fixed(2.seconds), 4)
//          _      <- assertIO(sleeps.head == 2.seconds)
//          _      <- assertIO(sleeps.tail.forall(_ <= 1.second))
//        } yield ()
//    }
//
//    "have correct exponential backoff policy" in scopeIO {
//      ctx =>
//
//        val baseDelay = 100
//        val policy    = RetryPolicy.exponential(baseDelay.millis)
//        def test(rf: RetryFunction[F, Any, FiniteDuration], now: ZonedDateTime, exp: Int): F[Nothing, Unit] = {
//          (for {
//            next <- rf(now, ()).map(_.asInstanceOf[ControllerDecision.Repeat[F, Any, FiniteDuration]])
//            _    <- assertIO(next.out == (baseDelay * math.pow(2.0, exp.toDouble)).toLong.millis)
//            res   = if (exp < 4) test(next.action, next.interval, exp + 1) else F.unit
//          } yield res).flatten
//        }
//
//        for {
//          now <- TestClock.now()
//          _   <- test(policy.action, now, 0)
//        } yield ()
//    }
//
//    "combine different policies properly" in scopeIO {
//      ctx =>
//
//        val intersectP = RetryPolicy.recursWhile[F, Boolean](identity) && RetryPolicy.recurs(4)
//        val unionP     = RetryPolicy.recursWhile[F, Boolean](identity) || RetryPolicy.recurs(4)
//        val eff        = (counter: Ref2[F, Int]) => counter.update(_ + 1).map(_ < 3)
//
//        for {
//          counter1 <- F.mkRef(0)
//          counter2 <- F.mkRef(0)
//          _        <- scheduler.repeat(eff(counter1))(intersectP)
//          res1     <- counter1.get
//          _        <- assertIO(res1 == 3)
//
//          _    <- scheduler.repeat(eff(counter2))(unionP)
//          res2 <- counter2.get
//          _    <- assertIO(res2 == 5)
//
//          np1      = RetryPolicy.spaced(300.millis) && RetryPolicy.spaced(200.millis)
//          delays1 <- runner(F.unit)(np1, 1)
//          _       <- assertIO(delays1 == Vector(300.millis))
//
//          np2      = RetryPolicy.spaced(300.millis) || RetryPolicy.spaced(200.millis)
//          delays2 <- runner(F.unit)(np2, 1)
//          _       <- assertIO(delays2 == Vector(200.millis))
//        } yield ()
//    }
//
//    "retry effect after fail" in scopeIO {
//      ctx =>
//
//        def test(maxRetries: Int, expected: Int) = {
//          val eff = (counter: Ref2[F, Int]) => counter.update(_ + 1).flatMap(v => if (v < 3) F.fail(new Throwable("Crap!")) else F.unit)
//          for {
//            counter <- F.mkRef(0)
//            _       <- scheduler.retryOrElse(eff(counter))(RetryPolicy.recurs(maxRetries))(_ => counter.set(-1))
//            res     <- counter.get
//            _       <- assertIO(res == expected)
//          } yield ()
//        }
//
//        for {
//          _ <- test(2, 3)
//          _ <- test(1, -1)
//        } yield ()
//    }
//
//    "should fail immediately if fail occurs during repeat" in scopeIO {
//      ctx =>
//
//        val eff = (counter: Ref2[F, Int]) => counter.update(_ + 1).flatMap(v => if (v < 2) F.fail(new Throwable("Crap!")) else F.unit)
//        for {
//          counter <- F.mkRef(0)
//          _       <- scheduler.repeat(eff(counter))(RetryPolicy.recurs(10)).asBad[Throwable]
//        } yield ()
//    }
//
//    def simpleCounter[A, B](policy: RetryPolicy[F, Int, B])(implicit sc: TGScheduler[F]) = {
//      for {
//        counter <- F.mkRef(0)
//        res     <- sc.repeat(counter.update(_ + 1))(policy)
//      } yield res
//    }
//
//    def runner[E, A, B](eff: F[E, Any])(policy: RetryPolicy[F, Any, B], n: Int)(implicit T: Temporal2[F], clock2: Clock2[F]): F[E, Vector[FiniteDuration]] = {
//      import scala.jdk.DurationConverters._
//      def loop(in: Any, makeDecision: RetryFunction[F, Any, B], acc: Vector[FiniteDuration], iter: Int): F[E, Vector[FiniteDuration]] = {
//        if (iter <= 0) F.pure(acc)
//        else {
//          (for {
//            now <- clock2.now()
//            dec <- makeDecision(now, in)
//            res = dec match {
//              case ControllerDecision.Stop(_) => F.pure(acc)
//              case ControllerDecision.Repeat(_, interval, next) =>
//                val sleep = java.time.Duration.between(now, interval).toScala
//                T.sleep(sleep) *> eff *> loop((), next, acc.appended(sleep), iter - 1)
//            }
//          } yield res).flatten
//        }
//      }
//
//      loop((), policy.action, Vector.empty[FiniteDuration], n)
//    }
//  }
//}
