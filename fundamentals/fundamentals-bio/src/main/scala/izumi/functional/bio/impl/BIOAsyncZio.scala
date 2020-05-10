package izumi.functional.bio.impl

import java.util.concurrent.CompletionStage

import izumi.functional.bio.BIOExit.ZIOExit
import izumi.functional.bio.{BIOAsync3, BIOExit, BIOFiber, BIOFiber3, BIOLocal, BIOMonad3, __PlatformSpecific}
import zio.ZIO.ZIOWithFilterOps
import zio.internal.ZIOSucceedNow
import zio.{NeedsEnv, ZIO}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object BIOAsyncZio extends BIOAsyncZio

class BIOAsyncZio extends BIOAsync3[ZIO] with BIOLocal[ZIO] {
  @inline override final def pure[A](a: A): ZIO[Any, Nothing, A] = ZIOSucceedNow(a)
  @inline override final def sync[A](effect: => A): ZIO[Any, Nothing, A] = ZIO.effectTotal(effect)
  @inline override final def syncThrowable[A](effect: => A): ZIO[Any, Throwable, A] = ZIO.effect(effect)
  @inline override final def suspend[R, A](effect: => ZIO[R, Throwable, A]): ZIO[R, Throwable, A] = ZIO.effectSuspend(effect)

  @inline override final def fail[E](v: => E): ZIO[Any, E, Nothing] = ZIO.fail(v)
  @inline override final def terminate(v: => Throwable): ZIO[Any, Nothing, Nothing] = ZIO.die(v)

  @inline override final def fromEither[E, A](effect: => Either[E, A]): ZIO[Any, E, A] = ZIO.fromEither(effect)
  @inline override final def fromOption[E, A](errorOnNone: => E)(effect: => Option[A]): ZIO[Any, E, A] = ZIO.fromEither(effect.toRight(errorOnNone))
  @inline override final def fromTry[A](effect: => Try[A]): ZIO[Any, Throwable, A] = ZIO.fromTry(effect)

  @inline override final def void[R, E, A](r: ZIO[R, E, A]): ZIO[R, E, Unit] = r.unit
  @inline override final def map[R, E, A, B](r: ZIO[R, E, A])(f: A => B): ZIO[R, E, B] = r.map(f)
  @inline override final def as[R, E, A, B](r: ZIO[R, E, A])(v: => B): ZIO[R, E, B] = r.as(v)

  @inline override final def tapError[R, E, A, E1 >: E](r: ZIO[R, E, A])(f: E => ZIO[R, E1, Unit]): ZIO[R, E1, A] = r.tapError(f)
  @inline override final def leftMap[R, E, A, E2](r: ZIO[R, E, A])(f: E => E2): ZIO[R, E2, A] = r.mapError(f)
  @inline override final def leftFlatMap[R, E, A, E2](r: ZIO[R, E, A])(f: E => ZIO[R, Nothing, E2]): ZIO[R, E2, A] = r.flatMapError(f)
  @inline override final def flip[R, E, A](r: ZIO[R, E, A]): ZIO[R, A, E] = r.flip
  @inline override final def bimap[R, E, A, E2, B](r: ZIO[R, E, A])(f: E => E2, g: A => B): ZIO[R, E2, B] = r.bimap(f, g)

  @inline override final def flatMap[R, E, A, B](r: ZIO[R, E, A])(f0: A => ZIO[R, E, B]): ZIO[R, E, B] = r.flatMap(f0)
  @inline override final def tap[R, E, A](r: ZIO[R, E, A])(f: A => ZIO[R, E, Unit]): ZIO[R, E, A] = r.tap(f)
  @inline override final def tapBoth[R, E, A, E1 >: E](r: ZIO[R, E, A])(err: E => ZIO[R, E1, Unit], succ: A => ZIO[R, E1, Unit]): ZIO[R, E1, A] = r.tapBoth(err, succ)
  @inline override final def flatten[R, E, A](r: ZIO[R, E, ZIO[R, E, A]]): ZIO[R, E, A] = ZIO.flatten(r)
  @inline override final def *>[R, E, A, B](f: ZIO[R, E, A], next: => ZIO[R, E, B]): ZIO[R, E, B] = f *> next
  @inline override final def <*[R, E, A, B](f: ZIO[R, E, A], next: => ZIO[R, E, B]): ZIO[R, E, A] = f <* next
  @inline override final def map2[R, E, A, B, C](r1: ZIO[R, E, A], r2: => ZIO[R, E, B])(f: (A, B) => C): ZIO[R, E, C] = r1.zipWith(r2)(f)

  @inline override final def redeem[R, E, A, E2, B](r: ZIO[R, E, A])(err: E => ZIO[R, E2, B], succ: A => ZIO[R, E2, B]): ZIO[R, E2, B] = r.foldM(err, succ)
  @inline override final def catchAll[R, E, A, E2](r: ZIO[R, E, A])(f: E => ZIO[R, E2, A]): ZIO[R, E2, A] = r.catchAll(f)
  @inline override final def catchSome[R, E, A, E1 >: E](r: ZIO[R, E, A])(f: PartialFunction[E, ZIO[R, E1, A]]): ZIO[R, E1, A] = r.catchSome(f)
  @inline override final def withFilter[R, E, A](r: ZIO[R, E, A])(predicate: A => Boolean)(implicit ev: NoSuchElementException <:< E): ZIO[R, E, A] =
    new ZIOWithFilterOps(r).withFilter(predicate)(ev)

  @inline override final def guarantee[R, E, A](f: ZIO[R, E, A], cleanup: ZIO[R, Nothing, Unit]): ZIO[R, E, A] = f.ensuring(cleanup)
  @inline override final def attempt[R, E, A](r: ZIO[R, E, A]): ZIO[R, Nothing, Either[E, A]] = r.either
  @inline override final def redeemPure[R, E, A, B](r: ZIO[R, E, A])(err: E => B, succ: A => B): ZIO[R, Nothing, B] = r.fold(err, succ)

  @inline override final def bracket[R, E, A, B](acquire: ZIO[R, E, A])(release: A => ZIO[R, Nothing, Unit])(use: A => ZIO[R, E, B]): ZIO[R, E, B] = {
    ZIO.bracket(acquire)(release)(use)
  }
  @inline override final def bracketCase[R, E, A, B](
    acquire: ZIO[R, E, A]
  )(release: (A, BIOExit[E, B]) => ZIO[R, Nothing, Unit]
  )(use: A => ZIO[R, E, B]
  ): ZIO[R, E, B] = {
    ZIO.bracketExit[R, E, A, B](acquire, (a, exit) => release(a, ZIOExit.toBIOExit(exit)), use)
  }
  @inline override final def guaranteeCase[R, E, A](f: ZIO[R, E, A], cleanup: BIOExit[E, A] => ZIO[R, Nothing, Unit]): ZIO[R, E, A] = {
    f.onExit(cleanup apply ZIOExit.toBIOExit(_))
  }

  @inline override final def traverse[R, E, A, B](l: Iterable[A])(f: A => ZIO[R, E, B]): ZIO[R, E, List[B]] = ZIO.foreach(l)(f)
  @inline override final def sequence[R, E, A, B](l: Iterable[ZIO[R, E, A]]): ZIO[R, E, List[A]] = ZIO.collectAll(l)
  @inline override final def traverse_[R, E, A](l: Iterable[A])(f: A => ZIO[R, E, Unit]): ZIO[R, E, Unit] = ZIO.foreach_(l)(f)
  @inline override final def sequence_[R, E](l: Iterable[ZIO[R, E, Unit]]): ZIO[R, E, Unit] = ZIO.foreach_(l)(identity)

  @inline override final def sandbox[R, E, A](r: ZIO[R, E, A]): ZIO[R, BIOExit.Failure[E], A] = r.sandbox.mapError(ZIOExit.toBIOExit[E])

  // BIOAsync

  @inline override final def yieldNow: ZIO[Any, Nothing, Unit] = ZIO.yieldNow
  @inline override final def never: ZIO[Any, Nothing, Nothing] = ZIO.never

  @inline override final def race[R, E, A](r1: ZIO[R, E, A], r2: ZIO[R, E, A]): ZIO[R, E, A] = {
    r1.interruptible.raceFirst(r2.interruptible)
  }
  @inline override final def racePair[R, E, A, B](
    r1: ZIO[R, E, A],
    r2: ZIO[R, E, B],
  ): ZIO[R, E, Either[(A, BIOFiber3[ZIO, E, B]), (BIOFiber3[ZIO, E, A], B)]] = {
    (r1.interruptible raceWith r2.interruptible)(
      { case (l, f) => l.fold(f.interrupt *> ZIO.halt(_), ZIOSucceedNow).map(lv => Left((lv, BIOFiber.fromZIO(f)))) },
      { case (r, f) => r.fold(f.interrupt *> ZIO.halt(_), ZIOSucceedNow).map(rv => Right((BIOFiber.fromZIO(f), rv))) },
    )
  }

  @inline override final def async[E, A](register: (Either[E, A] => Unit) => Unit): ZIO[Any, E, A] = {
    ZIO.effectAsync(cb => register(cb apply _.fold(ZIO.fail(_), ZIOSucceedNow)))
  }
  @inline override final def asyncF[R, E, A](register: (Either[E, A] => Unit) => ZIO[R, E, Unit]): ZIO[R, E, A] = {
    ZIO.effectAsyncM(cb => register(cb apply _.fold(ZIO.fail(_), ZIOSucceedNow)))
  }
  @inline override final def asyncCancelable[E, A](register: (Either[E, A] => Unit) => Canceler): ZIO[Any, E, A] = {
    ZIO.effectAsyncInterrupt[Any, E, A] {
      cb =>
        val canceler = register(cb apply _.fold(ZIO.fail(_), ZIOSucceedNow))
        Left(canceler)
    }
  }

  @inline override final def fromFuture[A](mkFuture: ExecutionContext => Future[A]): ZIO[Any, Throwable, A] = {
    ZIO.fromFuture(mkFuture)
  }
  @inline override final def fromFutureJava[A](javaFuture: => CompletionStage[A]): ZIO[Any, Throwable, A] = {
    __PlatformSpecific.fromFutureJava(javaFuture)
  }

  @inline override final def uninterruptible[R, E, A](r: ZIO[R, E, A]): ZIO[R, E, A] = r.uninterruptible

  @inline override final def parTraverseN[R, E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => ZIO[R, E, B]): ZIO[R, E, List[B]] =
    ZIO.foreachParN(maxConcurrent)(l)(f(_).interruptible)
  @inline override final def parTraverseN_[R, E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => ZIO[R, E, B]): ZIO[R, E, Unit] =
    ZIO.foreachParN_(maxConcurrent)(l)(f(_).interruptible)
  @inline override final def parTraverse[R, E, A, B](l: Iterable[A])(f: A => ZIO[R, E, B]): ZIO[R, E, List[B]] = ZIO.foreachPar(l)(f(_).interruptible)
  @inline override final def parTraverse_[R, E, A, B](l: Iterable[A])(f: A => ZIO[R, E, B]): ZIO[R, E, Unit] = ZIO.foreachPar_(l)(f(_).interruptible)

  @inline override final def zipWithPar[R, E, A, B, C](fa: ZIO[R, E, A], fb: ZIO[R, E, B])(f: (A, B) => C): ZIO[R, E, C] = fa.zipWithPar(fb)(f)
  @inline override final def zipPar[R, E, A, B](fa: ZIO[R, E, A], fb: ZIO[R, E, B]): ZIO[R, E, (A, B)] = fa <&> fb
  @inline override final def zipParLeft[R, E, A, B](fa: ZIO[R, E, A], fb: ZIO[R, E, B]): ZIO[R, E, A] = fa <& fb
  @inline override final def zipParRight[R, E, A, B](fa: ZIO[R, E, A], fb: ZIO[R, E, B]): ZIO[R, E, B] = fa &> fb

  @inline override final val InnerF: BIOMonad3[ZIO] = this

  @inline override final def ask[R]: ZIO[R, Nothing, R] = ZIO.environment
  @inline override final def askWith[R, A](f: R => A): ZIO[R, Nothing, A] = ZIO.access(f)

  @inline override final def provide[R, E, A](fr: ZIO[R, E, A])(r: => R): ZIO[Any, E, A] = fr.provide(r)(NeedsEnv)
  @inline override final def contramap[R, E, A, R0](fr: ZIO[R, E, A])(f: R0 => R): ZIO[R0, E, A] = fr.provideSome(f)(NeedsEnv)

  @inline override final def access[R, E, A](f: R => ZIO[R, E, A]): ZIO[R, E, A] = ZIO.accessM(f)

  @inline override final def dimap[R1, E, A1, R2, A2](fab: ZIO[R1, E, A1])(f: R2 => R1)(g: A1 => A2): ZIO[R2, E, A2] = fab.provideSome(f).map(g)

  @inline override final def andThen[R, R1, E, A](f: ZIO[R, E, R1], g: ZIO[R1, E, A]): ZIO[R, E, A] = f >>> g
  @inline override final def asking[R, E, A](f: ZIO[R, E, A]): ZIO[R, E, (A, R)] = f.onFirst

  @inline override final def choice[RL, RR, E, A](f: ZIO[RL, E, A], g: ZIO[RR, E, A]): ZIO[Either[RL, RR], E, A] = (f +++ g).map(_.merge)
  @inline override final def choose[RL, RR, E, AL, AR](f: ZIO[RL, E, AL], g: ZIO[RR, E, AR]): ZIO[Either[RL, RR], E, Either[AL, AR]] = f +++ g
}
