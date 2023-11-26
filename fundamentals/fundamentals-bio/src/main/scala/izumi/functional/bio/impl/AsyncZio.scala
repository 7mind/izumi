package izumi.functional.bio.impl

import izumi.functional.bio.Exit.ZIOExit
import izumi.functional.bio.data.{Morphism3, RestoreInterruption2}
import izumi.functional.bio.{Async2, Exit, Fiber2, __PlatformSpecific}
import izumi.fundamentals.platform.language.Quirks.Discarder
import zio._izumicompat_.{__ZIORaceCompat, __ZIOWithFiberRuntime}
import zio.internal.stacktracer.{InteropTracer, Tracer}
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.ZIO

import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object AsyncZio extends AsyncZio[Any]

open class AsyncZio[R] extends Async2[ZIO[R, +_, +_]] {
  @inline override final def InnerF: this.type = this

  @inline override final def unit: ZIO[Any, Nothing, Unit] = ZIO.unit
  @inline override final def pure[A](a: A): ZIO[Any, Nothing, A] = ZIO.succeed(a)(Tracer.instance.empty)
  @inline override final def sync[A](effect: => A): ZIO[Any, Nothing, A] = {
    val byName: () => A = () => effect
    implicit val trace: zio.Trace = InteropTracer.newTrace(byName)

    ZIO.succeed(effect)
  }
  @inline override final def syncThrowable[A](effect: => A): ZIO[Any, Throwable, A] = {
    val byName: () => A = () => effect
    implicit val trace: zio.Trace = InteropTracer.newTrace(byName)

    ZIO.attempt(effect)
  }
  @inline override final def suspend[A](effect: => ZIO[R, Throwable, A]): ZIO[R, Throwable, A] = {
    val byName: () => ZIO[R, Throwable, A] = () => effect
    implicit val trace: zio.Trace = InteropTracer.newTrace(byName)

    ZIO.suspend(effect)
  }
  @inline override final def suspendSafe[E, A](effect: => ZIO[R, E, A]): ZIO[R, E, A] = {
    val byName: () => ZIO[R, E, A] = () => effect
    implicit val trace: zio.Trace = InteropTracer.newTrace(byName)

    ZIO.suspendSucceed(effect)
  }

  @inline override final def fail[E](v: => E): ZIO[Any, E, Nothing] = {
    val byName: () => E = () => v
    implicit val trace: zio.Trace = InteropTracer.newTrace(byName)

    ZIO.fail(v)
  }
  @inline override final def terminate(v: => Throwable): ZIO[Any, Nothing, Nothing] = {
    val byName: () => Throwable = () => v
    implicit val trace: zio.Trace = InteropTracer.newTrace(byName)

    ZIO.die(v)
  }

  @inline override final def fromEither[E, A](effect: => Either[E, A]): ZIO[Any, E, A] = {
    val byName: () => Either[E, A] = () => effect
    implicit val trace: zio.Trace = InteropTracer.newTrace(byName)

    ZIO.fromEither(effect)
  }
  @inline override final def fromOption[E, A](errorOnNone: => E)(effect: => Option[A]): ZIO[Any, E, A] = {
    val byName: () => Option[A] = () => effect
    implicit val trace: zio.Trace = InteropTracer.newTrace(byName)

    ZIO.fromEither(effect.toRight(errorOnNone))
  }
  @inline override final def fromTry[A](effect: => Try[A]): ZIO[Any, Throwable, A] = {
    val byName: () => Try[A] = () => effect
    implicit val trace: zio.Trace = InteropTracer.newTrace(byName)

    ZIO.fromTry(effect)
  }

  @inline override final def void[E, A](r: ZIO[R, E, A]): ZIO[R, E, Unit] = r.unit(Tracer.instance.empty)
  @inline override final def map[E, A, B](r: ZIO[R, E, A])(f: A => B): ZIO[R, E, B] = r.map(f)(InteropTracer.newTrace(f))
  @inline override final def as[E, A, B](r: ZIO[R, E, A])(v: => B): ZIO[R, E, B] = {
    val byName: () => B = () => v
    implicit val trace: zio.Trace = InteropTracer.newTrace(byName)

    r.as(v)
  }

  @inline override final def tapError[E, A, E1 >: E](r: ZIO[R, E, A])(f: E => ZIO[R, E1, Unit]): ZIO[R, E1, A] = r.tapError(f)(implicitly, InteropTracer.newTrace(f))
  @inline override final def leftMap[E, A, E2](r: ZIO[R, E, A])(f: E => E2): ZIO[R, E2, A] = r.mapError(f)(implicitly, InteropTracer.newTrace(f))
  @inline override final def leftFlatMap[E, A, E2](r: ZIO[R, E, A])(f: E => ZIO[R, Nothing, E2]): ZIO[R, E2, A] =
    r.flatMapError(f)(implicitly, InteropTracer.newTrace(f))
  @inline override final def flip[E, A](r: ZIO[R, E, A]): ZIO[R, A, E] = r.flip(Tracer.instance.empty)
  @inline override final def bimap[E, A, E2, B](r: ZIO[R, E, A])(f: E => E2, g: A => B): ZIO[R, E2, B] = r.mapBoth(f, g)(implicitly, InteropTracer.newTrace(f))

  @inline override final def flatMap[E, A, B](r: ZIO[R, E, A])(f0: A => ZIO[R, E, B]): ZIO[R, E, B] = r.flatMap(f0)(InteropTracer.newTrace(f0))
  @inline override final def tap[E, A](r: ZIO[R, E, A], f: A => ZIO[R, E, Unit]): ZIO[R, E, A] = r.tap(f)(InteropTracer.newTrace(f))
  @inline override final def tapBoth[E, A, E1 >: E](r: ZIO[R, E, A])(err: E => ZIO[R, E1, Unit], succ: A => ZIO[R, E1, Unit]): ZIO[R, E1, A] =
    r.tapBoth(err, succ)(implicitly, InteropTracer.newTrace(err))
  @inline override final def flatten[E, A](r: ZIO[R, E, ZIO[R, E, A]]): ZIO[R, E, A] = ZIO.flatten(r)(Tracer.instance.empty)
  @inline override final def *>[E, A, B](f: ZIO[R, E, A], next: => ZIO[R, E, B]): ZIO[R, E, B] = {
    val byName: () => ZIO[R, E, B] = () => next
    implicit val trace: zio.Trace = InteropTracer.newTrace(byName)

    f *> next
  }
  @inline override final def <*[E, A, B](f: ZIO[R, E, A], next: => ZIO[R, E, B]): ZIO[R, E, A] = {
    val byName: () => ZIO[R, E, B] = () => next
    implicit val trace: zio.Trace = InteropTracer.newTrace(byName)

    f <* next
  }
  @inline override final def map2[E, A, B, C](r1: ZIO[R, E, A], r2: => ZIO[R, E, B])(f: (A, B) => C): ZIO[R, E, C] = {
    val byName: () => ZIO[R, E, B] = () => r2
    implicit val trace: zio.Trace = InteropTracer.newTrace(byName)

    r1.zipWith(r2)(f)
  }

  @inline override final def iterateWhile[E, A](r: ZIO[R, E, A])(p: A => Boolean): ZIO[R, E, A] = r.repeatWhile(p)(InteropTracer.newTrace(p))
  @inline override final def iterateUntil[E, A](r: ZIO[R, E, A])(p: A => Boolean): ZIO[R, E, A] = r.repeatUntil(p)(InteropTracer.newTrace(p))

  @inline override final def leftMap2[E, A, E2, E3](firstOp: ZIO[R, E, A], secondOp: => ZIO[R, E2, A])(f: (E, E2) => E3): ZIO[R, E3, A] = {
    val byName: () => ZIO[R, E2, A] = () => secondOp
    implicit val trace: zio.Trace = InteropTracer.newTrace(byName)

    firstOp.catchAll(e => secondOp.mapError(f(e, _)))
  }
  @inline override final def orElse[E, A, E2](r: ZIO[R, E, A], f: => ZIO[R, E2, A]): ZIO[R, E2, A] = {
    val byName: () => ZIO[R, E2, A] = () => f
    implicit val trace: zio.Trace = InteropTracer.newTrace(byName)

    r.orElse(f)
  }

  @inline override final def redeem[E, A, E2, B](r: ZIO[R, E, A])(err: E => ZIO[R, E2, B], succ: A => ZIO[R, E2, B]): ZIO[R, E2, B] =
    r.foldZIO(err, succ)(implicitly, InteropTracer.newTrace(err))
  @inline override final def catchAll[E, A, E2](r: ZIO[R, E, A])(f: E => ZIO[R, E2, A]): ZIO[R, E2, A] = r.catchAll(f)(implicitly, InteropTracer.newTrace(f))
  @inline override final def catchSome[E, A, E1 >: E](r: ZIO[R, E, A])(f: PartialFunction[E, ZIO[R, E1, A]]): ZIO[R, E1, A] =
    r.catchSome(f)(implicitly, InteropTracer.newTrace(f))

  @inline override final def guarantee[E, A](f: ZIO[R, E, A], cleanup: ZIO[R, Nothing, Unit]): ZIO[R, E, A] = f.ensuring(cleanup)(Tracer.instance.empty)
  @inline override final def attempt[E, A](r: ZIO[R, E, A]): ZIO[R, Nothing, Either[E, A]] = r.either(implicitly, Tracer.instance.empty)
  @inline override final def redeemPure[E, A, B](r: ZIO[R, E, A])(err: E => B, succ: A => B): ZIO[R, Nothing, B] =
    r.fold(err, succ)(implicitly, InteropTracer.newTrace(err))

  @inline override final def retryWhile[E, A](r: ZIO[R, E, A])(f: E => Boolean): ZIO[R, E, A] = r.retryWhile(f)(implicitly, InteropTracer.newTrace(f))
  @inline override final def retryWhileF[E, A](r: ZIO[R, E, A])(f: E => ZIO[R, Nothing, Boolean]): ZIO[R, E, A] =
    r.retryWhileZIO(f)(implicitly, InteropTracer.newTrace(f))

  @inline override final def retryUntil[E, A](r: ZIO[R, E, A])(f: E => Boolean): ZIO[R, E, A] = r.retryUntil(f)(implicitly, InteropTracer.newTrace(f))
  @inline override final def retryUntilF[E, A](r: ZIO[R, E, A])(f: E => ZIO[R, Nothing, Boolean]): ZIO[R, E, A] =
    r.retryUntilZIO(f)(implicitly, InteropTracer.newTrace(f))

  @inline override final def fromOptionOr[E, A](valueOnNone: => A, r: ZIO[R, E, Option[A]]): ZIO[R, E, A] = {
    val byName: () => A = () => valueOnNone
    implicit val trace: zio.Trace = InteropTracer.newTrace(byName)

    r.someOrElse(valueOnNone)
  }

  @inline override final def fromOptionF[E, A](fallbackOnNone: => ZIO[R, E, A], r: ZIO[R, E, Option[A]]): ZIO[R, E, A] = {
    val byName: () => ZIO[R, E, A] = () => fallbackOnNone
    implicit val trace: zio.Trace = InteropTracer.newTrace(byName)

    r.someOrElseZIO(fallbackOnNone)
  }

  @inline override final def bracket[E, A, B](acquire: ZIO[R, E, A])(release: A => ZIO[R, Nothing, Unit])(use: A => ZIO[R, E, B]): ZIO[R, E, B] = {
    ZIO.acquireReleaseWith(acquire)(release)(use)(InteropTracer.newTrace(release))
  }
  @inline override final def bracketCase[E, A, B](
    acquire: ZIO[R, E, A]
  )(release: (A, Exit[E, B]) => ZIO[R, Nothing, Unit]
  )(use: A => ZIO[R, E, B]
  ): ZIO[R, E, B] = {
    implicit val trace: zio.Trace = InteropTracer.newTrace(release)

    ZIO.acquireReleaseExitWith[R, E, A](acquire)((a, exit: zio.Exit[E, B]) => ZIOExit.withIsInterruptedF(i => release(a, ZIOExit.toExit(exit)(i))))(use)
  }
  @inline override final def guaranteeCase[E, A](f: ZIO[R, E, A], cleanup: Exit[E, A] => ZIO[R, Nothing, Unit]): ZIO[R, E, A] = {
    implicit val trace: zio.Trace = InteropTracer.newTrace(cleanup)

    f.onExit(exit => ZIOExit.withIsInterruptedF(cleanup apply ZIOExit.toExit(exit)(_)))
  }

  @inline override final def traverse[E, A, B](l: Iterable[A])(f: A => ZIO[R, E, B]): ZIO[R, E, List[B]] =
    ZIO.foreach(l.toList)(f)(implicitly, InteropTracer.newTrace(f))
  @inline override final def sequence[E, A](l: Iterable[ZIO[R, E, A]]): ZIO[R, E, List[A]] = ZIO.collectAll(l.toList)(implicitly, Tracer.instance.empty)
  @inline override final def traverse_[E, A](l: Iterable[A])(f: A => ZIO[R, E, Unit]): ZIO[R, E, Unit] = ZIO.foreachDiscard(l)(f)(InteropTracer.newTrace(f))
  @inline override final def sequence_[E](l: Iterable[ZIO[R, E, Unit]]): ZIO[R, E, Unit] = ZIO.foreachDiscard(l)(identity)(Tracer.instance.empty)
  @inline override final def filter[E, A](l: Iterable[A])(f: A => ZIO[R, E, Boolean]): ZIO[R, E, List[A]] = ZIO.filter(l.toList)(f)(implicitly, InteropTracer.newTrace(f))

  @inline override final def foldLeft[E, A, AC](l: Iterable[A])(z: AC)(f: (AC, A) => ZIO[R, E, AC]): ZIO[R, E, AC] = {
    ZIO.foldLeft(l)(z)(f)(InteropTracer.newTrace(f))
  }

  @inline override final def find[E, A](l: Iterable[A])(f: A => ZIO[R, E, Boolean]): ZIO[R, E, Option[A]] = {
    val trace = InteropTracer.newTrace(f)

    ZIO.collectFirst(l)(a => f(a).map(if (_) Some(a) else None)(trace))(trace)
  }

  @inline override final def collectFirst[E, A, B](l: Iterable[A])(f: A => ZIO[R, E, Option[B]]): ZIO[R, E, Option[B]] = {
    ZIO.collectFirst(l)(f)(InteropTracer.newTrace(f))
  }

  @inline override final def sandbox[E, A](r: ZIO[R, E, A]): ZIO[R, Exit.Failure[E], A] = {
    implicit val trace: zio.Trace = Tracer.instance.empty

    r.sandbox.flatMapError(ZIOExit withIsInterrupted ZIOExit.toExit(_))
  }

  @inline override final def yieldNow: ZIO[Any, Nothing, Unit] = ZIO.yieldNow(Tracer.instance.empty)
  @inline override final def never: ZIO[Any, Nothing, Nothing] = ZIO.never(Tracer.instance.empty)

  @inline override final def async[E, A](register: (Either[E, A] => Unit) => Unit): ZIO[Any, E, A] = {
    implicit val trace: zio.Trace = InteropTracer.newTrace(register)

    ZIO.async(cb => register(cb apply _.fold(ZIO.fail(_), ZIO.succeed(_))))
  }
  @inline override final def asyncF[E, A](register: (Either[E, A] => Unit) => ZIO[R, E, Unit]): ZIO[R, E, A] = {
    implicit val trace: zio.Trace = InteropTracer.newTrace(register)

    ZIO.asyncZIO(cb => register(cb apply _.fold(ZIO.fail(_), ZIO.succeed(_))))
  }
  @inline override final def asyncCancelable[E, A](register: (Either[E, A] => Unit) => Canceler): ZIO[R, E, A] = {
    implicit val trace: zio.Trace = InteropTracer.newTrace(register)

    ZIO.asyncInterrupt[R, E, A] {
      cb =>
        val canceler = register(cb apply _.fold(ZIO.fail(_), ZIO.succeed(_)))
        Left(canceler)
    }
  }

  @inline override final def fromFuture[A](mkFuture: ExecutionContext => Future[A]): ZIO[Any, Throwable, A] = {
    ZIO.fromFuture(mkFuture)(InteropTracer.newTrace(mkFuture))
  }
  @inline override final def fromFutureJava[A](javaFuture: => CompletionStage[A]): ZIO[Any, Throwable, A] = {
    __PlatformSpecific.fromFutureJava(javaFuture)
  }

  @inline override final def uninterruptible[E, A](r: ZIO[R, E, A]): ZIO[R, E, A] = r.uninterruptible(Tracer.instance.empty)

  @inline override final def race[E, A](r1: ZIO[R, E, A], r2: ZIO[R, E, A]): ZIO[R, E, A] = {
    implicit val trace: zio.Trace = Tracer.instance.empty

    __ZIORaceCompat.raceFirst(r1.interruptible, r2.interruptible)
  }

  @inline override final def racePairUnsafe[E, A, B](
    r1: ZIO[R, E, A],
    r2: ZIO[R, E, B],
  ): ZIO[R, E, Either[(Exit[E, A], Fiber2[ZIO[R, +_, +_], E, B]), (Fiber2[ZIO[R, +_, +_], E, A], Exit[E, B])]] = {
    implicit val trace: zio.Trace = Tracer.instance.empty

    val interrupted1 = new AtomicBoolean(true)
    val interrupted2 = new AtomicBoolean(true)
    __ZIORaceCompat.raceWith(
      ZIOExit.ZIOSignalOnNoExternalInterruptFailure(r1.interruptible)(sync(interrupted1.set(false))),
      ZIOExit.ZIOSignalOnNoExternalInterruptFailure(r2.interruptible)(sync(interrupted2.set(false))),
    )(
      { case (l, f) => ZIO.succeed(Left((ZIOExit.toExit(l)(interrupted1.get()), Fiber2.fromZIO(sync(interrupted2.get()))(f)))) },
      { case (r, f) => ZIO.succeed(Right((Fiber2.fromZIO(sync(interrupted1.get()))(f), ZIOExit.toExit(r)(interrupted2.get())))) },
    )
  }

  @inline override final def parTraverseN[E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => ZIO[R, E, B]): ZIO[R, E, List[B]] = {
    implicit val trace: zio.Trace = InteropTracer.newTrace(f)

    ZIO
      .foreachPar(l.toList)(f(_).interruptible)
      .withParallelism(maxConcurrent)
  }
  @inline override final def parTraverseN_[E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => ZIO[R, E, B]): ZIO[R, E, Unit] = {
    implicit val trace: zio.Trace = InteropTracer.newTrace(f)

    ZIO
      .foreachParDiscard(l)(f(_).interruptible)
      .withParallelism(maxConcurrent)
  }
  @inline override final def parTraverseNCore[E, A, B](l: Iterable[A])(f: A => ZIO[R, E, B]): ZIO[R, E, List[B]] = {
    ZIO.suspendSucceed(parTraverseN(java.lang.Runtime.getRuntime.availableProcessors() max 2)(l)(f))(InteropTracer.newTrace(f))
  }
  @inline override final def parTraverseNCore_[E, A, B](l: Iterable[A])(f: A => ZIO[R, E, B]): ZIO[R, E, Unit] = {
    ZIO.suspendSucceed(parTraverseN_(java.lang.Runtime.getRuntime.availableProcessors() max 2)(l)(f))(InteropTracer.newTrace(f))
  }
  @inline override final def parTraverse[E, A, B](l: Iterable[A])(f: A => ZIO[R, E, B]): ZIO[R, E, List[B]] = {
    implicit val trace: zio.Trace = InteropTracer.newTrace(f)

    // do not force unlimited parallelism here, obey 'regional parallelism' (unlimited by default)
    ZIO.foreachPar(l.toList)(f(_).interruptible)
  }
  @inline override final def parTraverse_[E, A, B](l: Iterable[A])(f: A => ZIO[R, E, B]): ZIO[R, E, Unit] = {
    implicit val trace: zio.Trace = InteropTracer.newTrace(f)

    // do not force unlimited parallelism here, obey 'regional parallelism' (unlimited by default)
    ZIO.foreachParDiscard(l)(f(_).interruptible)
  }

  @inline override final def zipWithPar[E, A, B, C](fa: ZIO[R, E, A], fb: ZIO[R, E, B])(f: (A, B) => C): ZIO[R, E, C] = {
    fa.zipWithPar(fb)(f)(InteropTracer.newTrace(f))
  }
  @inline override final def zipPar[E, A, B](fa: ZIO[R, E, A], fb: ZIO[R, E, B]): ZIO[R, E, (A, B)] = {
    fa.<&>(fb)(zio.Zippable.Zippable2[A, B], Tracer.instance.empty)
  }
  @inline override final def zipParLeft[E, A, B](fa: ZIO[R, E, A], fb: ZIO[R, E, B]): ZIO[R, E, A] = {
    fa.<&(fb)(Tracer.instance.empty)
  }
  @inline override final def zipParRight[E, A, B](fa: ZIO[R, E, A], fb: ZIO[R, E, B]): ZIO[R, E, B] = {
    fa.&>(fb)(Tracer.instance.empty)
  }

  @inline override final def sendInterruptToSelf: ZIO[Any, Nothing, Unit] = {
    implicit val trace: zio.Trace = Tracer.instance.empty

    def loopUntilInterrupted: ZIO[Any, Nothing, Unit] =
      ZIO.descriptorWith(d => if (d.interrupters.isEmpty) ZIO.yieldNow *> loopUntilInterrupted else ZIO.unit)

    for {
      _ <- __ZIOWithFiberRuntime.ZIOWithFiberRuntime[Any, Nothing, Unit]((thisFiber, _) => thisFiber.interruptAsFork(thisFiber.id))
      _ <- loopUntilInterrupted
    } yield ()
  }

  @inline override final def currentEC: ZIO[Any, Nothing, ExecutionContext] = ZIO.executor(Tracer.instance.empty).map(_.asExecutionContext)(Tracer.instance.empty)
  @inline override final def onEC[E, A](ec: ExecutionContext)(f: ZIO[R, E, A]): ZIO[R, E, A] = f.onExecutionContext(ec)(Tracer.instance.empty)

  @inline override final def uninterruptibleExcept[E, A](r: RestoreInterruption2[ZIO[R, +_, +_]] => ZIO[R, E, A]): ZIO[R, E, A] = {
    implicit val trace: zio.Trace = InteropTracer.newTrace(r)

    ZIO.uninterruptibleMask {
      restore =>
        val restoreMorphism: Morphism3[ZIO, ZIO] = Morphism3(restore(_))
        r(restoreMorphism)
    }
  }

  @inline override final def bracketExcept[E, A, B](
    acquire: RestoreInterruption2[ZIO[R, +_, +_]] => ZIO[R, E, A]
  )(release: (A, Exit[E, B]) => ZIO[R, Nothing, Unit]
  )(use: A => ZIO[R, E, B]
  ): ZIO[R, E, B] = {
    implicit val trace: zio.Trace = InteropTracer.newTrace(acquire)

    ZIO.uninterruptibleMask[R, E, B] {
      restore =>
        val restoreMorphism: Morphism3[ZIO, ZIO] = Morphism3(restore(_))
        acquire(restoreMorphism).flatMap {
          a =>
            ZIO
              .suspendSucceed(restore(use(a)))
              .exit
              .flatMap {
                e =>
                  ZIOExit
                    .withIsInterruptedF(i => release(a, Exit.ZIOExit.toExit(e)(i)))
                    .foldCauseZIO(
                      cause2 => ZIO.refailCause(e.foldExit(_ ++ cause2, _ => cause2)),
                      _ => ZIO.done(e),
                    )
              }
        }
    }
  }

  disableAutoTrace.discard()
}
