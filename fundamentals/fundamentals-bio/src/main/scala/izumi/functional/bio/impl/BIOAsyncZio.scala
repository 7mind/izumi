package izumi.functional.bio.impl

import java.util.concurrent.CompletionStage

import izumi.functional.bio.BIOExit.ZIOExit
import izumi.functional.bio.instances.BIOAsync3
import izumi.functional.bio.{BIOExit, BIOFiber, BIOFiber3, __PlatformSpecific}
import zio.ZIO
import zio.ZIO.ZIOWithFilterOps
import zio.compatrc18.zio_succeed_Now.succeedNow

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object BIOAsyncZio extends BIOAsyncZio

class BIOAsyncZio extends BIOAsync3[ZIO[-?, +?, +?]] {
  private[this] final type IO[R, +E, +A] = ZIO[R, E, A]

  @inline override final def pure[A](a: A): IO[Any, Nothing, A] = succeedNow(a)
  @inline override final def sync[A](effect: => A): IO[Any, Nothing, A] = ZIO.effectTotal(effect)
  @inline override final def syncThrowable[A](effect: => A): IO[Any, Throwable, A] = ZIO.effect(effect)
  @inline override final def suspend[R, A](effect: => IO[R, Throwable, A]): IO[R, Throwable, A] = ZIO.effectSuspend(effect)

  @inline override final def fail[E](v: => E): IO[Any, E, Nothing] = ZIO.fail(v)
  @inline override final def terminate(v: => Throwable): IO[Any, Nothing, Nothing] = ZIO.die(v)

  @inline override final def fromEither[L, R0](v: => Either[L, R0]): IO[Any, L, R0] = ZIO.fromEither(v)
  @inline override final def fromTry[A](effect: => Try[A]): IO[Any, Throwable, A] = ZIO.fromTry(effect)

  @inline override final def void[R, E, A](r: IO[R, E, A]): IO[R, E, Unit] = r.unit
  @inline override final def map[R, E, A, B](r: IO[R, E, A])(f: A => B): IO[R, E, B] = r.map(f)
  @inline override final def as[R, E, A, B](r: IO[R, E, A])(v: => B): IO[R, E, B] = r.as(v)

  @inline override final def tapError[R, E, A, E1 >: E](r: IO[R, E, A])(f: E => IO[R, E1, Unit]): IO[R, E1, A] = r.tapError(f)
  @inline override final def leftMap[R, E, A, E2](r: IO[R, E, A])(f: E => E2): IO[R, E2, A] = r.mapError(f)
  @inline override final def leftFlatMap[R, E, A, E2](r: IO[R, E, A])(f: E => IO[R, Nothing, E2]): IO[R, E2, A] = r.flatMapError(f)
  @inline override final def flip[R, E, A](r: IO[R, E, A]): IO[R, A, E] = r.flip
  @inline override final def bimap[R, E, A, E2, B](r: IO[R, E, A])(f: E => E2, g: A => B): IO[R, E2, B] = r.bimap(f, g)

  @inline override final def flatMap[R, E, A, E1 >: E, B](r: IO[R, E, A])(f0: A => IO[R, E1, B]): IO[R, E1, B] = r.flatMap(f0)
  @inline override final def tap[R, E, A, E2 >: E](r: IO[R, E, A])(f: A => IO[R, E2, Unit]): IO[R, E2, A] = r.tap(f)
  @inline override final def tapBoth[R, E, A, E2 >: E](r: IO[R, E, A])(err: E => IO[R, E2, Unit], succ: A => IO[R, E2, Unit]): IO[R, E2, A] = r.tapBoth(err, succ)
  @inline override final def flatten[R, E, A](r: IO[R, E, IO[R, E, A]]): IO[R, E, A] = ZIO.flatten(r)
  @inline override final def *>[R, E, A, B](f: IO[R, E, A], next: => IO[R, E, B]): IO[R, E, B] = f *> next
  @inline override final def <*[R, E, A, B](f: IO[R, E, A], next: => IO[R, E, B]): IO[R, E, A] = f <* next
  @inline override final def map2[R, E, A, B, C](r1: IO[R, E, A], r2: => IO[R, E, B])(f: (A, B) => C): IO[R, E, C] = r1.zipWith(r2)(f)

  @inline override final def redeem[R, E, A, E2, B](r: IO[R, E, A])(err: E => IO[R, E2, B], succ: A => IO[R, E2, B]): IO[R, E2, B] = r.foldM(err, succ)
  @inline override final def catchAll[R, E, A, E2, A2 >: A](r: IO[R, E, A])(f: E => IO[R, E2, A2]): IO[R, E2, A2] = r.catchAll(f)
  @inline override final def catchSome[R, E, A, E2 >: E, A2 >: A](r: ZIO[R, E, A])(f: PartialFunction[E, ZIO[R, E2, A2]]): ZIO[R, E2, A2] = r.catchSome(f)
  @inline override final def withFilter[R, E, A](r: IO[R, E, A])(predicate: A => Boolean)(implicit ev: NoSuchElementException <:< E): IO[R, E, A] =
    new ZIOWithFilterOps(r).withFilter(predicate)(ev)

  @inline override final def guarantee[R, E, A](f: IO[R, E, A])(cleanup: IO[R, Nothing, Unit]): IO[R, E, A] = f.ensuring(cleanup)
  @inline override final def attempt[R, E, A](r: IO[R, E, A]): IO[R, Nothing, Either[E, A]] = r.either
  @inline override final def redeemPure[R, E, A, B](r: IO[R, E, A])(err: E => B, succ: A => B): IO[R, Nothing, B] = r.fold(err, succ)

  @inline override final def bracket[R, E, A, B](acquire: IO[R, E, A])(release: A => IO[R, Nothing, Unit])(use: A => IO[R, E, B]): IO[R, E, B] = {
    ZIO.bracket(acquire)(release)(use)
  }

  @inline override final def bracketCase[R, E, A, B](acquire: IO[R, E, A])(release: (A, BIOExit[E, B]) => IO[R, Nothing, Unit])(use: A => IO[R, E, B]): IO[R, E, B] = {
    ZIO.bracketExit[R, E, A, B](acquire, { case (a, exit) => release(a, ZIOExit.toBIOExit(exit)) }, use)
  }

  @inline override final def traverse[R, E, A, B](l: Iterable[A])(f: A => IO[R, E, B]): IO[R, E, List[B]] = ZIO.foreach(l)(f)
  @inline override final def sequence[R, E, A, B](l: Iterable[IO[R, E, A]]): IO[R, E, List[A]] = ZIO.collectAll(l)
  @inline override final def traverse_[R, E, A](l: Iterable[A])(f: A => IO[R, E, Unit]): IO[R, E, Unit] = ZIO.foreach_(l)(f)
  @inline override final def sequence_[R, E](l: Iterable[IO[R, E, Unit]]): IO[R, E, Unit] = ZIO.foreach_(l)(identity)

  @inline override final def sandbox[R, E, A](r: IO[R, E, A]): IO[R, BIOExit.Failure[E], A] = r.sandbox.mapError(ZIOExit.toBIOExit[E])

  // BIOAsync

  @inline override final def yieldNow: IO[Any, Nothing, Unit] = ZIO.yieldNow
  @inline override final def never: IO[Any, Nothing, Nothing] = ZIO.never

  @inline override final def race[R, E, A](r1: IO[R, E, A], r2: IO[R, E, A]): IO[R, E, A] = r1.interruptible.raceFirst(r2.interruptible)

  @inline override final def racePair[R, E, A, B](
    r1: IO[R, E, A],
    r2: IO[R, E, B]
  ): IO[R, E, Either[(A, BIOFiber3[ZIO[-?, +?, +?], R, E, B]), (BIOFiber3[ZIO[-?, +?, +?], R, E, A], B)]] = {
    (r1.interruptible raceWith r2.interruptible)(
      { case (l, f) => l.fold(f.interrupt *> ZIO.halt(_), succeedNow).map(lv => Left((lv, BIOFiber.fromZIO(f)))) },
      { case (r, f) => r.fold(f.interrupt *> ZIO.halt(_), succeedNow).map(rv => Right((BIOFiber.fromZIO(f), rv))) }
    )
  }

  @inline override final def async[E, A](register: (Either[E, A] => Unit) => Unit): IO[Any, E, A] = {
    ZIO.effectAsync(cb => register(cb apply _.fold(ZIO.fail(_), succeedNow)))
  }

  @inline override final def asyncF[R, E, A](register: (Either[E, A] => Unit) => ZIO[R, E, Unit]): ZIO[R, E, A] = {
    ZIO.effectAsyncM(cb => register(cb apply _.fold(ZIO.fail(_), succeedNow)))
  }

  @inline override final def asyncCancelable[E, A](register: (Either[E, A] => Unit) => Canceler): IO[Any, E, A] = {
    ZIO.effectAsyncInterrupt[Any, E, A] {
      cb =>
        val canceler = register(cb apply _.fold(ZIO.fail(_), succeedNow))
        Left(canceler)
    }
  }

  @inline override final def fromFuture[A](mkFuture: ExecutionContext => Future[A]): IO[Any, Throwable, A] = {
    ZIO.fromFuture(mkFuture)
  }

  @inline override final def fromFutureJava[A](javaFuture: => CompletionStage[A]): IO[Any, Throwable, A] = {
    __PlatformSpecific.fromFutureJava(javaFuture)
  }

  @inline override final def uninterruptible[R, E, A](r: IO[R, E, A]): IO[R, E, A] = r.uninterruptible

  @inline override final def parTraverseN[R, E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => IO[R, E, B]): IO[R, E, List[B]] =
    ZIO.foreachParN(maxConcurrent)(l)(f(_).interruptible)
  @inline override final def parTraverseN_[R, E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => ZIO[R, E, B]): ZIO[R, E, Unit] =
    ZIO.foreachParN_(maxConcurrent)(l)(f(_).interruptible)
  @inline override final def parTraverse[R, E, A, B](l: Iterable[A])(f: A => ZIO[R, E, B]): ZIO[R, E, List[B]] = ZIO.foreachPar(l)(f(_).interruptible)
  @inline override final def parTraverse_[R, E, A, B](l: Iterable[A])(f: A => ZIO[R, E, B]): ZIO[R, E, Unit] = ZIO.foreachPar_(l)(f(_).interruptible)
}
