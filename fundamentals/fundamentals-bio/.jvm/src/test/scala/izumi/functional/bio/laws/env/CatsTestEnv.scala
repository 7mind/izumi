package izumi.functional.bio.laws.env

import cats.Eq
import cats.effect.IO
import cats.effect.kernel.Outcome
import cats.effect.testkit.TestInstances
import cats.kernel.Order
import izumi.functional.bio.impl.CatsToBIO.Bifunctorized
import org.scalacheck.{Arbitrary, Cogen, Prop}

import scala.concurrent.duration.FiniteDuration

trait CatsTestEnv extends TestInstances with EqThrowable {

  implicit def cogenIO2[A: Cogen](implicit ticker: Ticker): Cogen[Bifunctorized[IO, Throwable, A]] =
    cogenIO[A].contramap(_.unwrap)

  implicit def arbitraryIO2[A: Arbitrary: Cogen](implicit ticker: Ticker): Arbitrary[Bifunctorized[IO, Throwable, A]] = {
    Arbitrary(arbitraryIO[A].arbitrary.map(Bifunctorized.convertThrowable(_)))
  }

  implicit def orderIo2FiniteDuration(implicit ticker: Ticker): Order[Bifunctorized[IO, Throwable, FiniteDuration]] =
    Order.by(_.unwrap)

  override implicit def eqIOA[A: Eq](implicit ticker: Ticker): Eq[cats.effect.IO[A]] = {
    (ioa, iob) =>
      {
        val a = unsafeRun(ioa)
        val b = unsafeRun(iob)
        Eq[Outcome[Option, Throwable, A]].eqv(a, b) || {
          System.err.println(s"not equal a=$a b=$b")
          false
        }
      }
  }

  implicit def eqIOA2[A: Eq](implicit ticker: Ticker): Eq[Bifunctorized[IO, Throwable, A]] =
    Eq.by(_.unwrap)

  implicit def ioBooleanToProp2(implicit ticker: Ticker): Bifunctorized[IO, Throwable, Boolean] => Prop =
    iob => ioBooleanToProp(iob.unwrap)

//  implicit def clock2(implicit ticker: Ticker): Clock2[IO] = {
//    new Clock2[IO] {
//      override def epoch: IO[Nothing, Long] = UIO(ticker.ctx.now().toMillis)
//      override def now(accuracy: Clock1.ClockAccuracy): IO[Nothing, ZonedDateTime] = UIO(
//        ZonedDateTime.ofInstant(Instant.ofEpochMilli(ticker.ctx.now().toMillis), ZoneOffset.UTC)
//      )
//      override def nowLocal(accuracy: Clock1.ClockAccuracy): IO[Nothing, LocalDateTime] = UIO(
//        LocalDateTime.ofInstant(Instant.ofEpochMilli(ticker.ctx.now().toMillis), ZoneOffset.UTC)
//      )
//      override def nowOffset(accuracy: Clock1.ClockAccuracy): IO[Nothing, OffsetDateTime] = UIO(
//        OffsetDateTime.ofInstant(Instant.ofEpochMilli(ticker.ctx.now().toMillis), ZoneOffset.UTC)
//      )
//      override def monotonicNano: IO[Nothing, Long] = UIO(ticker.ctx.now().toNanos)
//    }
//  }

}
