package izumi.functional.bio.impl

import cats.Parallel
import cats.effect.Outcome
import cats.effect.kernel.{Async, Deferred, Fiber, Poll, Ref => CatsRef}
import cats.effect.std.{Semaphore => CatsSemaphore}
import izumi.functional.bio.data.{InterruptAction, Morphism2, RestoreInterruption2}
import izumi.functional.bio.{
  Async2,
  Bifunctorized,
  BlockingIO2,
  Exit,
  Fiber2,
  Fork2,
  Primitives2,
  Promise2,
  Ref2,
  Semaphore2,
  SubmergedTypedError,
  Temporal2,
}
import izumi.reflect.TagK

import java.util.concurrent.CompletionStage
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{CancellationException, ExecutionContext, Future}

/** CE → BIO conversion factory.
  *
  * Lifts a monofunctor effect type `F[_]` with a `cats.effect.kernel.Async[F]` instance into a
  * bifunctor effect type `Bifunctorized[F, +_, +_]` carrying the full BIO typeclass intersection
  * (`Async2 & Temporal2 & Fork2 & BlockingIO2 & Primitives2`).
  *
  * Typed errors raised via `fail(e)` are submerged into `F`'s Throwable channel as
  * [[SubmergedTypedError]] discriminated by `TagK[F]`. Defects raised via `terminate(t)` or
  * thrown synchronously remain as raw `Throwable`s — Goal 2 ("defects use monofunctor's raw
  * Throwable").
  *
  * This file imports `cats.*` directly. It is reachable from user classpath only when the user
  * has opted in via `import izumi.functional.bio.CatsToBIOConversions.*`. Goal 5 ("No More
  * Orphans") is preserved because [[izumi.functional.bio.package]] does not aggregate this
  * file's imports.
  */
object CatsToBIO {

  /** Build the full BIO typeclass intersection on `Bifunctorized[F, +_, +_]` from a
    * `cats.effect.kernel.Async[F]` plus `TagK[F]` for submerged-error discrimination.
    */
  def asyncToBIO[F[_]](
    implicit F: Async[F],
    tag: TagK[F],
  ): Async2[Bifunctorized[F, +_, +_]] &
    Temporal2[Bifunctorized[F, +_, +_]] &
    Fork2[Bifunctorized[F, +_, +_]] &
    BlockingIO2[Bifunctorized[F, +_, +_]] &
    Primitives2[Bifunctorized[F, +_, +_]] = {
    new Async2[Bifunctorized[F, +_, +_]]
      with Temporal2[Bifunctorized[F, +_, +_]]
      with Fork2[Bifunctorized[F, +_, +_]]
      with BlockingIO2[Bifunctorized[F, +_, +_]]
      with Primitives2[Bifunctorized[F, +_, +_]] {

      private[this] implicit val P: Parallel[F] = cats.effect.instances.spawn.parallelForGenSpawn(F)

      // Define `adapt` before `convertThrowable` to avoid forward-ref initialization order.
      private[this] val adapt: PartialFunction[Throwable, Throwable] = { case t: Throwable => SubmergedTypedError[F](t) }

      private[this] def convertThrowable[A](f: F[A]): Bifunctorized[F, Throwable, A] =
        Bifunctorized.assert(F.adaptError(f)(adapt))

      private[this] def outcomeToExit[E, A](outcome: Outcome[F, Throwable, A]): Bifunctorized[F, Nothing, Exit[E, A]] = outcome match {
        case Outcome.Succeeded(fa) =>
          Bifunctorized.assert(F.map(fa)(Exit.Success(_)))
        case Outcome.Errored(exc) =>
          exc match {
            case SubmergedTypedError(payload) =>
              pure(Exit.Error(payload.asInstanceOf[E], Exit.Trace.ThrowableTrace(exc)))
            case t =>
              pure(Exit.Termination(t, Exit.Trace.ThrowableTrace(t)))
          }
        case Outcome.Canceled() =>
          pure(Exit.Interruption(Nil, Exit.Trace.forUnknownError))
      }

      private[this] def fromPoll(poll: Poll[F]): RestoreInterruption2[Bifunctorized[F, +_, +_]] = {
        Morphism2[Bifunctorized[F, +_, +_], Bifunctorized[F, +_, +_]](f => Bifunctorized.assert(poll(f.unwrap)))
      }

      private[this] def toFiber2[E, A](fiber: Fiber[F, Throwable, A]): Fiber2[Bifunctorized[F, +_, +_], E, A] = {
        new Fiber2[Bifunctorized[F, +_, +_], E, A] {
          override def join: Bifunctorized[F, E, A] =
            Bifunctorized.assert(fiber.joinWith(F.flatMap(F.canceled)(_ => F.raiseError(new CancellationException("The fiber was canceled")))))
          override def observe: Bifunctorized[F, Nothing, Exit[E, A]] = flatMap(Bifunctorized.assert(fiber.join))(outcomeToExit[E, A])
          override def interrupt: Bifunctorized[F, Nothing, Unit] = Bifunctorized.assert(fiber.cancel)
        }
      }

      override def InnerF: this.type = this

      override def async[E, A](register: (Either[E, A] => Unit) => Unit): Bifunctorized[F, E, A] = {
        Bifunctorized.assert(F.async_[A](cb => register(e => cb(e.left.map(payload => SubmergedTypedError[F](payload))))))
      }
      override def asyncF[E, A](register: (Either[E, A] => Unit) => Bifunctorized[F, E, Unit]): Bifunctorized[F, E, A] = {
        Bifunctorized.assert(F.async[A] {
          cb =>
            F.as(register(e => cb(e.left.map(payload => SubmergedTypedError[F](payload)))).unwrap, None)
        })
      }
      override def asyncWithOnInterrupt[E, A](register: (Either[E, A] => Unit) => InterruptAction[Bifunctorized[F, +_, +_]]): Bifunctorized[F, E, A] = {
        Bifunctorized.assert(F.async[A] {
          cb =>
            F.map[InterruptAction[Bifunctorized[F, +_, +_]], Option[F[Unit]]](F.delay(register(e => cb(e.left.map(payload => SubmergedTypedError[F](payload))))))(
              ia => Some(ia.interrupt.unwrap)
            )
        })
      }
      override def fromFuture[A](mkFuture: ExecutionContext => Future[A]): Bifunctorized[F, Throwable, A] = {
        convertThrowable(F.fromFuture(F.flatMap(F.executionContext)(ec => F.delay(mkFuture(ec)))))
      }
      override def fromFutureJava[A](javaFuture: => CompletionStage[A]): Bifunctorized[F, Throwable, A] = {
        // CompletionStage has `.toCompletableFuture` since Java 8; CE3's Async.fromCompletableFuture takes F[CompletableFuture[A]].
        convertThrowable(F.fromCompletableFuture(F.delay(javaFuture.toCompletableFuture)))
      }
      override def currentEC: Bifunctorized[F, Nothing, ExecutionContext] = {
        Bifunctorized.assert(F.executionContext)
      }
      override def onEC[E, A](ec: ExecutionContext)(f: Bifunctorized[F, E, A]): Bifunctorized[F, E, A] = {
        Bifunctorized.assert(F.evalOn(f.unwrap, ec))
      }
      override def sleep(duration: Duration): Bifunctorized[F, Nothing, Unit] = {
        Bifunctorized.assert(duration match {
          case _: Duration.Infinite => F.never
          case finite: FiniteDuration => F.sleep(finite)
        })
      }
      override def timeout[E, A](duration: Duration)(r: Bifunctorized[F, E, A]): Bifunctorized[F, E, Option[A]] = {
        race(map(r)(Some(_)), as(sleep(duration))(None))
      }
      override def fork[E, A](f: Bifunctorized[F, E, A]): Bifunctorized[F, Nothing, Fiber2[Bifunctorized[F, +_, +_], E, A]] = {
        map(Bifunctorized.assert[F, Nothing, Fiber[F, Throwable, A]](F.start(f.unwrap)))(toFiber2[E, A])
      }
      override def forkOn[E, A](ec: ExecutionContext)(f: Bifunctorized[F, E, A]): Bifunctorized[F, Nothing, Fiber2[Bifunctorized[F, +_, +_], E, A]] = {
        map(Bifunctorized.assert[F, Nothing, Fiber[F, Throwable, A]](F.startOn(f.unwrap, ec)))(toFiber2[E, A])
      }

      override def syncBlocking[A](f: => A): Bifunctorized[F, Throwable, A] = {
        convertThrowable(F.blocking(f))
      }
      override def syncInterruptibleBlocking[A](f: => A): Bifunctorized[F, Throwable, A] = {
        convertThrowable(F.interruptible(f))
      }
      override def pure[A](a: A): Bifunctorized[F, Nothing, A] = {
        Bifunctorized.assert(F.pure(a))
      }
      override def terminate(v: => Throwable): Bifunctorized[F, Nothing, Nothing] = {
        // Goal 2: defects use monofunctor's raw Throwable. No submerging here.
        Bifunctorized.assert(F.raiseError(v))
      }
      override def sandbox[E, A](r: Bifunctorized[F, E, A]): Bifunctorized[F, Exit.FailureUninterrupted[E], A] = {
        Bifunctorized.assert(
          F.handleErrorWith(r.unwrap) {
            case exc @ SubmergedTypedError(payload) =>
              fail(Exit.Error(payload.asInstanceOf[E], Exit.Trace.ThrowableTrace(exc))).unwrap.asInstanceOf[F[A]]
            case t =>
              fail(Exit.Termination(t, Exit.Trace.ThrowableTrace(t))).unwrap.asInstanceOf[F[A]]
          }
        )
      }
      override def sendInterruptToSelf: Bifunctorized[F, Nothing, Unit] = {
        Bifunctorized.assert(F.canceled)
      }

      override def fail[E](v: => E): Bifunctorized[F, E, Nothing] = {
        Bifunctorized.assert(F.raiseError(SubmergedTypedError[F](v)))
      }

      override def shiftBlocking[E, A](f: Bifunctorized[F, E, A]): Bifunctorized[F, E, A] = {
        // CE3's Async does not expose a dedicated blocking EC; passthrough is the conservative default.
        f
      }

      override def mkRef[A](a: A): Bifunctorized[F, Nothing, Ref2[Bifunctorized[F, +_, +_], A]] = {
        Bifunctorized.assert[F, Nothing, Ref2[Bifunctorized[F, +_, +_], A]](
          F.map(CatsRef.of[F, A](a)) {
            (ref: CatsRef[F, A]) =>
              new Ref2[Bifunctorized[F, +_, +_], A] {
                override def get: Bifunctorized[F, Nothing, A] = Bifunctorized.assert(ref.get)
                override def set(a: A): Bifunctorized[F, Nothing, Unit] = Bifunctorized.assert(ref.set(a))
                override def modify[B](f: A => (B, A)): Bifunctorized[F, Nothing, B] = Bifunctorized.assert(ref.modify(a => f(a).swap))
                override def update(f: A => A): Bifunctorized[F, Nothing, A] = Bifunctorized.assert(ref.updateAndGet(f))
                override def update_(f: A => A): Bifunctorized[F, Nothing, Unit] = Bifunctorized.assert(ref.update(f))
                override def tryModify[B](f: A => (B, A)): Bifunctorized[F, Nothing, Option[B]] =
                  Bifunctorized.assert(ref.tryModify(a => f(a).swap))
                override def tryUpdate(f: A => A): Bifunctorized[F, Nothing, Option[A]] = {
                  // CE Ref returns Boolean from tryUpdate; we mirror izumi's Option[A] by tryModify-ing the new value out.
                  Bifunctorized.assert(F.map(ref.tryModify(a => { val newA = f(a); (newA, newA) }))(identity))
                }
              }
          }
        )
      }

      override def mkPromise[E, A]: Bifunctorized[F, Nothing, Promise2[Bifunctorized[F, +_, +_], E, A]] = {
        // Carry the typed BIO effect through the cats Deferred — matches the pattern of
        // existing Promise2.fromCats (which stores `F[E, A]` inside `Deferred[F[Throwable, _], F[E, A]]`).
        // The Bifunctorized carrier already encodes success / typed fail / defect uniformly.
        Bifunctorized.assert[F, Nothing, Promise2[Bifunctorized[F, +_, +_], E, A]](
          F.map(Deferred[F, Bifunctorized[F, E, A]]) {
            (deferred: Deferred[F, Bifunctorized[F, E, A]]) =>
              new Promise2[Bifunctorized[F, +_, +_], E, A] {
                override def await: Bifunctorized[F, E, A] = {
                  Bifunctorized.assert(F.flatMap(deferred.get)((b: Bifunctorized[F, E, A]) => b.unwrap))
                }
                override def poll: Bifunctorized[F, Nothing, Option[Bifunctorized[F, E, A]]] = {
                  Bifunctorized.assert(deferred.tryGet)
                }
                override def succeed(a: A): Bifunctorized[F, Nothing, Boolean] =
                  Bifunctorized.assert(deferred.complete(Bifunctorized.assert(F.pure(a))))
                override def fail(e: E): Bifunctorized[F, Nothing, Boolean] =
                  Bifunctorized.assert(deferred.complete(Bifunctorized.assert(F.raiseError(SubmergedTypedError[F](e)))))
                override def terminate(t: Throwable): Bifunctorized[F, Nothing, Boolean] =
                  Bifunctorized.assert(deferred.complete(Bifunctorized.assert(F.raiseError(t))))
              }
          }
        )
      }

      override def mkSemaphore(permits: Long): Bifunctorized[F, Nothing, Semaphore2[Bifunctorized[F, +_, +_]]] = {
        Bifunctorized.assert[F, Nothing, Semaphore2[Bifunctorized[F, +_, +_]]](
          F.map(CatsSemaphore[F](permits)) {
            (sem: CatsSemaphore[F]) =>
              new Semaphore2[Bifunctorized[F, +_, +_]] {
                override def acquire: Bifunctorized[F, Nothing, Unit] = Bifunctorized.assert(sem.acquire)
                override def release: Bifunctorized[F, Nothing, Unit] = Bifunctorized.assert(sem.release)
                override def acquireN(n: Long): Bifunctorized[F, Nothing, Unit] = Bifunctorized.assert(sem.acquireN(n))
                override def releaseN(n: Long): Bifunctorized[F, Nothing, Unit] = Bifunctorized.assert(sem.releaseN(n))
                override def lifecycle: izumi.functional.lifecycle.Lifecycle[Bifunctorized[F, Nothing, _], Unit] = {
                  // Construct from primitive acquire/release; constraint-free and equivalent to the cats `permit` resource.
                  izumi.functional.lifecycle.Lifecycle.make[Bifunctorized[F, Nothing, _], Unit](acquire)(_ => release)
                }
              }
          }
        )
      }

      override def race[E, A](r1: Bifunctorized[F, E, A], r2: Bifunctorized[F, E, A]): Bifunctorized[F, E, A] = {
        Bifunctorized.assert(F.map(F.race(r1.unwrap, r2.unwrap))((e: Either[A, A]) => e.fold(identity[A], identity[A])))
      }

      override def racePairUnsafe[E, A, B](fa: Bifunctorized[F, E, A], fb: Bifunctorized[F, E, B]): Bifunctorized[F, E, Either[
        (Exit[E, A], Fiber2[Bifunctorized[F, +_, +_], E, B]),
        (Fiber2[Bifunctorized[F, +_, +_], E, A], Exit[E, B]),
      ]] = {
        flatMap(Bifunctorized.assert[F, E, Either[
          (Outcome[F, Throwable, A], Fiber[F, Throwable, B]),
          (Fiber[F, Throwable, A], Outcome[F, Throwable, B]),
        ]](F.racePair(fa.unwrap, fb.unwrap))) {
          case Left((o, f)) => map(outcomeToExit[E, A](o))(e => Left((e, toFiber2[E, B](f))))
          case Right((f, o)) => map(outcomeToExit[E, B](o))(e => Right((toFiber2[E, A](f), e)))
        }
      }

      override def yieldNow: Bifunctorized[F, Nothing, Unit] = {
        Bifunctorized.assert(F.cede)
      }
      override def parTraverse[E, A, B](l: Iterable[A])(f: A => Bifunctorized[F, E, B]): Bifunctorized[F, E, List[B]] = {
        Bifunctorized.assert(Parallel.parTraverse(l.toList)(f.asInstanceOf[A => F[B]]))
      }
      override def parTraverseN[E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => Bifunctorized[F, E, B]): Bifunctorized[F, E, List[B]] = {
        Bifunctorized.assert(F.parTraverseN(maxConcurrent)(l.toList)(f.asInstanceOf[A => F[B]]))
      }
      override def parTraverseNCore[E, A, B](l: Iterable[A])(f: A => Bifunctorized[F, E, B]): Bifunctorized[F, E, List[B]] = {
        val cores = (java.lang.Runtime.getRuntime.availableProcessors() max 2)
        parTraverseN(cores)(l)(f)
      }
      override def zipWithPar[E, A, B, C](fa: Bifunctorized[F, E, A], fb: Bifunctorized[F, E, B])(f: (A, B) => C): Bifunctorized[F, E, C] = {
        Bifunctorized.assert(Parallel.parMap2(fa.unwrap, fb.unwrap)(f))
      }

      override def bracketCase[E, A, B](
        acquire: Bifunctorized[F, E, A]
      )(release: (A, Exit[E, B]) => Bifunctorized[F, Nothing, Unit]
      )(use: A => Bifunctorized[F, E, B]
      ): Bifunctorized[F, E, B] = Bifunctorized.assert(F.bracketCase(acquire = acquire.unwrap)(use = use.asInstanceOf[A => F[B]])(release = {
        (a, outcome) => flatMap(outcomeToExit[E, B](outcome))(release(a, _)).unwrap.asInstanceOf[F[Unit]]
      }))

      override def uninterruptibleExcept[E, A](r: RestoreInterruption2[Bifunctorized[F, +_, +_]] => Bifunctorized[F, E, A]): Bifunctorized[F, E, A] = {
        Bifunctorized.assert(F.uncancelable(poll => r(fromPoll(poll)).unwrap))
      }

      override def bracketExcept[E, A, B](
        acquire: RestoreInterruption2[Bifunctorized[F, +_, +_]] => Bifunctorized[F, E, A]
      )(release: (A, Exit[E, B]) => Bifunctorized[F, Nothing, Unit]
      )(use: A => Bifunctorized[F, E, B]
      ): Bifunctorized[F, E, B] = {
        Bifunctorized.assert(
          F.bracketFull(acquire = poll => acquire(fromPoll(poll)).unwrap)(
            use = use.asInstanceOf[A => F[B]]
          )(release = (a, outcome) => flatMap(outcomeToExit[E, B](outcome))(release(a, _)).unwrap.asInstanceOf[F[Unit]])
        )
      }

      override def syncThrowable[A](effect: => A): Bifunctorized[F, Throwable, A] = {
        convertThrowable(F.delay(effect))
      }
      override def sync[A](effect: => A): Bifunctorized[F, Nothing, A] = {
        Bifunctorized.assert(F.delay(effect))
      }
      override def catchAll[E, A, E2](r: Bifunctorized[F, E, A])(f: E => Bifunctorized[F, E2, A]): Bifunctorized[F, E2, A] = {
        Bifunctorized.assert(F.recoverWith(r.unwrap) {
          case SubmergedTypedError(payload) => f(payload.asInstanceOf[E]).unwrap.asInstanceOf[F[A]]
          // Un-matched Throwables propagate as defects / Termination — Goal 2.
        })
      }
      override def flatMap[E, A, B](r: Bifunctorized[F, E, A])(f: A => Bifunctorized[F, E, B]): Bifunctorized[F, E, B] = {
        Bifunctorized.assert(F.flatMap(r.unwrap)(f.asInstanceOf[A => F[B]]))
      }
      override def unit: Bifunctorized[F, Nothing, Unit] = {
        Bifunctorized.assert(F.unit)
      }
    }
  }

}
