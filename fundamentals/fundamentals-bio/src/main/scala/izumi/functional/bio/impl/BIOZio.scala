package izumi.functional.bio.impl

import java.util.concurrent.CompletionStage

import izumi.functional.bio.BIOExit.ZIOExit
import izumi.functional.bio.{BIOAsync, BIOExit, BIOFiber, BIOTemporal, __PlatformSpecific}
import zio.ZIO.ZIOWithFilterOps
import zio.clock.Clock
import zio.internal.ZIOSucceedNow
import zio.duration.Duration.fromScala
import zio.{Schedule, ZIO}

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object BIOZio extends BIOZio[Any]

class BIOZio[R] extends BIOAsync[ZIO[R, +?, +?]] {
  private[this] final type IO[+E, +A] = ZIO[R, E, A]

  @inline override final def pure[A](a: A): IO[Nothing, A] = ZIOSucceedNow(a)
  @inline override final def sync[A](effect: => A): IO[Nothing, A] = ZIO.effectTotal(effect)
  @inline override final def syncThrowable[A](effect: => A): IO[Throwable, A] = ZIO.effect(effect)
  @inline override final def suspend[A](effect: => IO[Throwable, A]): IO[Throwable, A] = ZIO.effectSuspend(effect)

  @inline override final def fail[E](v: => E): IO[E, Nothing] = ZIO.fail(v)
  @inline override final def terminate(v: => Throwable): IO[Nothing, Nothing] = ZIO.die(v)

  @inline override final def fromEither[L, R0](v: => Either[L, R0]): IO[L, R0] = ZIO.fromEither(v)
  @inline override final def fromTry[A](effect: => Try[A]): IO[Throwable, A] = ZIO.fromTry(effect)

  @inline override final def void[E, A](r: IO[E, A]): IO[E, Unit] = r.unit
  @inline override final def map[E, A, B](r: IO[E, A])(f: A => B): IO[E, B] = r.map(f)
  @inline override final def as[E, A, B](r: IO[E, A])(v: => B): IO[E, B] = r.as(v)

  @inline override final def tapError[E, A, E1 >: E](r: IO[E, A])(f: E => IO[E1, Unit]): IO[E1, A] = r.tapError(f)
  @inline override final def leftMap[E, A, E2](r: IO[E, A])(f: E => E2): IO[E2, A] = r.mapError(f)
  @inline override final def leftFlatMap[E, A, E2](r: IO[E, A])(f: E => IO[Nothing, E2]): IO[E2, A] = r.flatMapError(f)
  @inline override final def flip[E, A](r: IO[E, A]): IO[A, E] = r.flip
  @inline override final def bimap[E, A, E2, B](r: IO[E, A])(f: E => E2, g: A => B): IO[E2, B] = r.bimap(f, g)

  @inline override final def flatMap[E, A, E1 >: E, B](r: IO[E, A])(f0: A => IO[E1, B]): IO[E1, B] = r.flatMap(f0)
  @inline override final def tap[E, A, E2 >: E](r: IO[E, A])(f: A => IO[E2, Unit]): IO[E2, A] = r.tap(f)
  @inline override final def tapBoth[E, A, E2 >: E](r: IO[E, A])(err: E => IO[E2, Unit], succ: A => IO[E2, Unit]): IO[E2, A] = r.tapBoth(err, succ)
  @inline override final def flatten[E, A](r: IO[E, IO[E, A]]): IO[E, A] = ZIO.flatten(r)
  @inline override final def *>[E, A, B](f: IO[E, A], next: => IO[E, B]): IO[E, B] = f *> next
  @inline override final def <*[E, A, B](f: IO[E, A], next: => IO[E, B]): IO[E, A] = f <* next
  @inline override final def map2[E, A, B, C](r1: IO[E, A], r2: => IO[E, B])(f: (A, B) => C): IO[E, C] = r1.zipWith(r2)(f)

  @inline override final def redeem[E, A, E2, B](r: IO[E, A])(err: E => IO[E2, B], succ: A => IO[E2, B]): IO[E2, B] = r.foldM(err, succ)
  @inline override final def catchAll[E, A, E2, A2 >: A](r: IO[E, A])(f: E => IO[E2, A2]): IO[E2, A2] = r.catchAll(f)
  @inline override final def catchSome[E, A, E2 >: E, A2 >: A](r: ZIO[R, E, A])(f: PartialFunction[E, ZIO[R, E2, A2]]): ZIO[R, E2, A2] = r.catchSome(f)
  @inline override final def withFilter[E, A](r: IO[E, A])(predicate: A => Boolean)(implicit ev: NoSuchElementException <:< E): IO[E, A] =
    new ZIOWithFilterOps(r).withFilter(predicate)(ev)

  @inline override final def guarantee[E, A](f: IO[E, A])(cleanup: IO[Nothing, Unit]): IO[E, A] = f.ensuring(cleanup)
  @inline override final def attempt[E, A](r: IO[E, A]): IO[Nothing, Either[E, A]] = r.either
  @inline override final def redeemPure[E, A, B](r: IO[E, A])(err: E => B, succ: A => B): IO[Nothing, B] = r.fold(err, succ)

  @inline override final def bracket[E, A, B](acquire: IO[E, A])(release: A => IO[Nothing, Unit])(use: A => IO[E, B]): IO[E, B] = {
    ZIO.bracket(acquire)(release)(use)
  }

  @inline override final def bracketCase[E, A, B](acquire: IO[E, A])(release: (A, BIOExit[E, B]) => IO[Nothing, Unit])(use: A => IO[E, B]): IO[E, B] = {
    ZIO.bracketExit[R, E, A, B](acquire, { case (a, exit) => release(a, ZIOExit.toBIOExit(exit)) }, use)
  }

  @inline override final def traverse[E, A, B](l: Iterable[A])(f: A => IO[E, B]): IO[E, List[B]] = ZIO.foreach(l)(f)
  @inline override final def sequence[E, A, B](l: Iterable[IO[E, A]]): IO[E, List[A]] = ZIO.collectAll(l)
  @inline override final def traverse_[E, A](l: Iterable[A])(f: A => IO[E, Unit]): IO[E, Unit] = ZIO.foreach_(l)(f)
  @inline override final def sequence_[E](l: Iterable[IO[E, Unit]]): IO[E, Unit] = ZIO.foreach_(l)(identity)

  @inline override final def sandbox[E, A](r: IO[E, A]): IO[BIOExit.Failure[E], A] = r.sandbox.mapError(ZIOExit.toBIOExit[E])

  // BIOAsync

  @inline override final def yieldNow: IO[Nothing, Unit] = ZIO.yieldNow
  @inline override final def never: IO[Nothing, Nothing] = ZIO.never

  @inline override final def race[E, A](r1: IO[E, A], r2: IO[E, A]): IO[E, A] = r1.interruptible.raceFirst(r2.interruptible)

  @inline override final def racePair[E, A, B](r1: IO[E, A], r2: IO[E, B]): IO[E, Either[(A, BIOFiber[ZIO[R, +?, +?], E, B]), (BIOFiber[ZIO[R, +?, +?], E, A], B)]] = {
    (r1.interruptible raceWith r2.interruptible)(
      { case (l, f) => l.fold(f.interrupt *> ZIO.halt(_), ZIOSucceedNow).map(lv => Left((lv, BIOFiber.fromZIO(f)))) },
      { case (r, f) => r.fold(f.interrupt *> ZIO.halt(_), ZIOSucceedNow).map(rv => Right((BIOFiber.fromZIO(f), rv))) }
    )
  }

  @inline override final def async[E, A](register: (Either[E, A] => Unit) => Unit): IO[E, A] = {
    ZIO.effectAsync(cb => register(cb apply _.fold(ZIO.fail(_), ZIOSucceedNow)))
  }

  @inline override final def asyncF[E, A](register: (Either[E, A] => Unit) => ZIO[R, E, Unit]): ZIO[R, E, A] = {
    ZIO.effectAsyncM(cb => register(cb apply _.fold(ZIO.fail(_), ZIOSucceedNow)))
  }

  @inline override final def asyncCancelable[E, A](register: (Either[E, A] => Unit) => Canceler): IO[E, A] = {
    ZIO.effectAsyncInterrupt[R, E, A] {
      cb =>
        val canceler = register(cb apply _.fold(ZIO.fail(_), ZIOSucceedNow))
        Left(canceler)
    }
  }

  @inline override final def fromFuture[A](mkFuture: ExecutionContext => Future[A]): IO[Throwable, A] = {
    ZIO.fromFuture(mkFuture)
  }

  @inline override final def fromFutureJava[A](javaFuture: => CompletionStage[A]): IO[Throwable, A] = {
    __PlatformSpecific.fromFutureJava(javaFuture)
  }

  @inline override final def uninterruptible[E, A](r: IO[E, A]): IO[E, A] = r.uninterruptible

  @inline override final def parTraverseN[E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => IO[E, B]): IO[E, List[B]] = ZIO.foreachParN(maxConcurrent)(l)(f(_).interruptible)
  @inline override final def parTraverseN_[E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => ZIO[R, E, B]): ZIO[R, E, Unit] = ZIO.foreachParN_(maxConcurrent)(l)(f(_).interruptible)
  @inline override final def parTraverse[E, A, B](l: Iterable[A])(f: A => ZIO[R, E, B]): ZIO[R, E, List[B]] = ZIO.foreachPar(l)(f(_).interruptible)
  @inline override final def parTraverse_[E, A, B](l: Iterable[A])(f: A => ZIO[R, E, B]): ZIO[R, E, Unit] = ZIO.foreachPar_(l)(f(_).interruptible)
}

class BIOTemporalZio[R](private val clock: Clock) extends BIOZio[R] with BIOTemporal[ZIO[R, +?, +?]] {
  @inline override final def sleep(duration: Duration): ZIO[R, Nothing, Unit] = {
    ZIO.sleep(fromScala(duration)).provide(clock)
  }

  @inline override final def retryOrElse[A, E, A2 >: A, E2](r: ZIO[R, E, A])(duration: FiniteDuration, orElse: => ZIO[R, E2, A2]): ZIO[R, E2, A2] =
    ZIO.accessM { env =>
      val zioDuration = Schedule.duration(fromScala(duration))

      r.provide(env)
        .retryOrElse(zioDuration, (_: Any, _: Any) => orElse.provide(env))
        .provide(clock)
    }

  @inline override final def timeout[E, A](r: ZIO[R, E, A])(duration: Duration): ZIO[R, E, Option[A]] = {
    ZIO.accessM[R](e => race(r.provide(e).map(Some(_)).interruptible, sleep(duration).as(None).interruptible))
  }

}
