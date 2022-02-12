//package izumi.functional.bio.impl
//
//import cats.effect.kernel.Clock
//import izumi.functional.bio.Temporal2
//import monix.bio.{IO, UIO}
//
//import scala.concurrent.duration.*
//
//class TemporalMonix(clock: Clock[UIO]) extends AsyncMonix with Temporal2[IO] {
//  @inline override def sleep(duration: Duration): IO[Nothing, Unit] = IO.sleep(FiniteDuration(duration.length, duration.unit))
//  @inline override def retryOrElse[R, E, A, E2](r: IO[E, A])(duration: FiniteDuration, orElse: => IO[E2, A]): IO[E2, A] = {
//    def loop(maxTime: FiniteDuration): IO[E2, A] = {
//      r.redeemCauseWith(
//        _ =>
//          for {
//            now <- clock.monotonic
//            res <- if (now < maxTime) loop(maxTime) else orElse
//          } yield res,
//        res => IO.pure(res),
//      )
//    }
//
//    clock.monotonic
//      .flatMap(now => loop(maxTime = now + duration))
//  }
//
//  @inline override def timeout[R, E, A](duration: Duration)(r: IO[E, A]): IO[E, Option[A]] = {
//    r.timeout(FiniteDuration(duration.length, duration.unit))
//  }
//}
