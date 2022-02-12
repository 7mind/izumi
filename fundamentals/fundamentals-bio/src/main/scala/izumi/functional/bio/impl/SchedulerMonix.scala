//package izumi.functional.bio.impl
//
//import cats.effect.kernel.Clock
//import izumi.functional.bio.Temporal2
//import izumi.functional.bio.__VersionSpecificDurationConvertersCompat.toFiniteDuration
//import izumi.functional.bio.retry.RetryPolicy.{ControllerDecision, RetryFunction}
//import izumi.functional.bio.retry.{RetryPolicy, Scheduler2, toZonedDateTime}
//import monix.bio.{IO, UIO}
//
//class SchedulerMonix(clock: Clock[UIO]) extends Scheduler2[IO] {
//  override def repeat[E, A, B](eff: IO[E, A])(policy: RetryPolicy[IO, A, B]): IO[E, A] = {
//    def loop(in: A, makeDecision: RetryFunction[IO, A, B]): IO[E, A] = {
//      (for {
//        now <- clock.realTime.map(epoch => toZonedDateTime(epoch.toMillis))
//        dec <- makeDecision(now, in)
//        res = dec match {
//          case ControllerDecision.Repeat(_, interval, action) =>
//            val sleepTime = toFiniteDuration(java.time.Duration.between(now, interval))
//            IO.sleep(sleepTime) *> eff.flatMap(loop(_, action))
//          case ControllerDecision.Stop(_) =>
//            IO.pure(in)
//        }
//      } yield res).flatten
//    }
//
//    eff.flatMap(out => loop(out, policy.action))
//  }
//
//  override def retry[E, E1 >: E, S, A](eff: IO[E, A])(policy: RetryPolicy[IO, E1, S]): IO[E, A] = {
//    retryOrElse(eff)(policy)(IO.raiseError)
//  }
//
//  override def retryOrElse[E, E1 >: E, E2, A, A2 >: A, S](eff: IO[E, A])(policy: RetryPolicy[IO, E1, S])(orElse: E1 => IO[E2, A2]): IO[E2, A2] = {
//    def loop(in: E1, makeDecision: RetryFunction[IO, E1, S]): IO[E2, A2] = {
//      (for {
//        now <- clock.realTime.map(epoch => toZonedDateTime(epoch.toMillis))
//        dec <- makeDecision(now, in)
//        res = dec match {
//          case ControllerDecision.Repeat(_, interval, action) =>
//            val sleepTime = toFiniteDuration(java.time.Duration.between(now, interval))
//            IO.sleep(sleepTime) *> eff.catchAll(loop(_, action))
//          case ControllerDecision.Stop(_) =>
//            orElse(in)
//        }
//      } yield res).flatten
//    }
//
//    eff.catchAll(err => loop(err, policy.action))
//  }
//}
