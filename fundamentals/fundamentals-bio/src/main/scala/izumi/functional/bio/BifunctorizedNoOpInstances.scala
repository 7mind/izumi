package izumi.functional.bio

import izumi.functional.bio.PredefinedHelper.Predefined
import izumi.functional.bio.data.InterruptAction
import izumi.functional.bio.impl.MiniBIO

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{Future, Promise}

/** High-priority no-op identity instances for `Bifunctorized.NoOp[F, +_, +_]` when `F` is
  * already a bifunctor with a BIO `IO2` instance. The "no-op" is a type-level reinterpretation:
  * `Bifunctorized.NoOp[F, E, A]` is `F[E, A]` at runtime (cast via `asInstanceOf`), so the
  * existing `IO2[F]` instance can be cast directly to `IO2[Bifunctorized.NoOp[F, +_, +_]]`.
  *
  * Outranks PR-04's `CatsToBIOConversions.AsyncToBIO` (`NotPredefined.Of`) because this trait's
  * factory returns `Predefined.Of`. Mixed into `object Bifunctorized` so the implicit is in the
  * implicit scope of `Bifunctorized.NoOp[F, ?, ?]` typeclass searches — this ensures the no-op
  * is auto-available for `IO2[NoOp[F, ?, ?]]` lookups without polluting general
  * `Functor2[X]` / `IO2[X]` searches with an unbound `X`.
  *
  * Also provides the Identity special-case [[identityBifunctorizedHasIO2]] (Goal 3) — see its
  * scaladoc for the load-bearing rationale.
  */
trait BifunctorizedNoOpInstances {

  /** High-priority no-op identity instance for any bifunctor `F[+_, +_]` that already carries
    * a BIO `IO2` instance. Casts the existing `IO2[F]` dictionary to `IO2[NoOp[F, +_, +_]]` —
    * sound because `NoOp[F, E, A]` is an abstract type erased to `Object` at the JVM, identical
    * in representation to `F[E, A]`. The cast does not allocate.
    *
    * Outranks PR-04's `CatsToBIOConversions.AsyncToBIO` (`NotPredefined.Of`) when both apply
    * because this factory returns `Predefined.Of`.
    */
  @inline implicit final def bifunctorIsAlreadyBifunctor[F[+_, +_]](
    implicit F: IO2[F]
  ): Predefined.Of[IO2[Bifunctorized.NoOp[F, +_, +_]]] =
    Predefined(F.asInstanceOf[IO2[Bifunctorized.NoOp[F, +_, +_]]])

  /** Identity special-case (Goal 3): an [[IO2]] instance for [[Bifunctorized.IdentityBifunctorized]]
    * delegating to [[izumi.functional.bio.impl.MiniBIO.IOForMiniBIO]] via cast.
    *
    * UNLIKE [[bifunctorIsAlreadyBifunctor]], this factory does NOT erase to the type-level identity
    * of `Identity` (which would be the runtime carrier `A`). Instead, the runtime carrier of every
    * [[Bifunctorized.IdentityBifunctorized]] value is a [[izumi.functional.bio.impl.MiniBIO MiniBIO]]
    * (boxed), so the `IO2[MiniBIO]` dictionary is directly applicable. The cast is sound because
    * `Bifunctorized.IdentityBifunctorized` is an abstract type erased to `Object`, identical in
    * representation to `MiniBIO[E, A]`.
    *
    * Returned as `Predefined.Of` so it outranks any cats-effect `Sync[Identity]`-mediated path
    * that some user might bring into scope (none currently exists, but it costs nothing to be safe).
    */
  @inline implicit final def identityBifunctorizedHasIO2: Predefined.Of[IO2[Bifunctorized.IdentityBifunctorized]] =
    Predefined(MiniBIO.IOForMiniBIO.asInstanceOf[IO2[Bifunctorized.IdentityBifunctorized]])

  /** [[Primitives2]] instance for [[Bifunctorized.IdentityBifunctorized]]. The carrier is
    * MiniBIO which is single-threaded synchronous; mutable references / promises / semaphores
    * are backed by `java.util.concurrent.atomic` primitives wrapped in `MiniBIO.Sync` nodes.
    *
    * Promise/Semaphore are not used by typical Identity workflows (which run synchronously without
    * forks); the implementations throw on async-only operations like `Promise2.await` for unset
    * promises and `Semaphore2.acquire` past the capacity. Sync-style usage works correctly.
    */
  @inline implicit final def identityBifunctorizedHasPrimitives2: Primitives2[Bifunctorized.IdentityBifunctorized] =
    PrimitivesForIdentityBifunctorized.asInstanceOf[Primitives2[Bifunctorized.IdentityBifunctorized]]

  /** [[Parallel2]] instance for [[Bifunctorized.IdentityBifunctorized]]. Backed by MiniBIO which is
    * single-threaded synchronous; "parallel" traversals collapse to sequential `IO2.traverse`.
    * This is semantically correct for a single-threaded carrier — `parTraverse` MUST run the elements
    * but the order/concurrency contract is unobservable when there is no concurrency primitive.
    *
    * Sole consumer in the testkit: [[izumi.distage.testkit.runner.impl.services.ParTraverseExt]]
    * forwards `Parallelism.Unlimited`/`Parallelism.Fixed` traversals to this instance when the
    * inner test effect is `IdentityBifunctorized` (i.e. [[izumi.distage.testkit.scalatest.SpecIdentity]]
    * tests).
    *
    * Returned as `Predefined.Of` to outrank the `ConvertFromParallel[F]` derivation in
    * [[izumi.functional.bio.Root]] (which would otherwise derive `Monad2[IdentityBifunctorized] & S4`
    * from this `Parallel2` instance and conflict with the higher-priority
    * [[identityBifunctorizedHasIO2]] in `Functor2` implicit search on Scala 2).
    */
  @inline implicit final def identityBifunctorizedHasParallel2: Predefined.Of[Parallel2[Bifunctorized.IdentityBifunctorized]] =
    Predefined(ParallelForIdentityBifunctorized.asInstanceOf[Parallel2[Bifunctorized.IdentityBifunctorized]])

  /** [[izumi.functional.bio.unsafe.UnsafeRun2 UnsafeRun2]] instance for
    * [[Bifunctorized.IdentityBifunctorized]]. Runs MiniBIO synchronously via
    * [[izumi.functional.bio.impl.MiniBIO.run]] — no thread pool, no async, no interruption.
    *
    * Required by the testkit runner (`TestPlanner` registers `UnsafeRun2[TestF]` as a root in
    * the per-test injector). For [[izumi.distage.testkit.scalatest.SpecIdentity]] tests the inner
    * `TestF` is `IdentityBifunctorized`, and this instance provides the synchronous unsafe-run
    * entry point that the runner invokes to execute the test body.
    */
  @inline implicit final def identityBifunctorizedHasUnsafeRun2: UnsafeRun2[Bifunctorized.IdentityBifunctorized] =
    UnsafeRunForIdentityBifunctorized.asInstanceOf[UnsafeRun2[Bifunctorized.IdentityBifunctorized]]

  /** [[WeakTemporal2]] instance for [[Bifunctorized.IdentityBifunctorized]]. Delegates to
    * [[izumi.functional.bio.impl.MiniBIO.IOForMiniBIO]]'s `WeakTemporal2` capability — `sleep`
    * blocks the calling thread via `Thread.sleep`, `timeout` runs the effect to completion
    * (single-threaded synchronous carrier has no concurrency primitive to race a timer).
    *
    * This restores the pre-bifunctorization `QuasiTemporal[Identity]` capability, which the
    * testkit Identity test variants (`DistageSequentialSuitesTestIdentity`,
    * `DistageParallelLevelTestIdentity`, `IdentityDistageSleepTest*`) require for their
    * Thread.sleep-based assertions of test-level parallelism bounds.
    */
  @inline implicit final def identityBifunctorizedHasWeakTemporal2: Predefined.Of[WeakTemporal2[Bifunctorized.IdentityBifunctorized]] =
    Predefined(MiniBIO.IOForMiniBIO.asInstanceOf[WeakTemporal2[Bifunctorized.IdentityBifunctorized]])

  /** Backing Parallel2 implementation for `IdentityBifunctorized` — sequential traversals over MiniBIO. */
  private object ParallelForIdentityBifunctorized extends Parallel2[MiniBIO] {
    override val InnerF: Monad2[MiniBIO] = MiniBIO.IOForMiniBIO

    override def parTraverse[E, A, B](l: Iterable[A])(f: A => MiniBIO[E, B]): MiniBIO[E, List[B]] =
      InnerF.traverse(l)(f)

    override def parTraverseN[E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => MiniBIO[E, B]): MiniBIO[E, List[B]] =
      InnerF.traverse(l)(f)

    override def parTraverseNCore[E, A, B](l: Iterable[A])(f: A => MiniBIO[E, B]): MiniBIO[E, List[B]] =
      InnerF.traverse(l)(f)

    override def zipWithPar[E, A, B, C](fa: MiniBIO[E, A], fb: MiniBIO[E, B])(f: (A, B) => C): MiniBIO[E, C] =
      InnerF.map2(fa, fb)(f)
  }

  /** Backing UnsafeRun2 implementation for `IdentityBifunctorized` — synchronous MiniBIO runner.
    *
    * Each `unsafeRun*` method calls `io.run()` on the calling thread. The Future-returning methods
    * return an already-completed future; interruption is a no-op (`InterruptAction(unit)`) because
    * MiniBIO does not support interruption.
    */
  private object UnsafeRunForIdentityBifunctorized extends UnsafeRun2[MiniBIO] {
    override def unsafeRun[E, A](io: => MiniBIO[E, A]): A = io.run() match {
      case Exit.Success(value) => value
      // For typed errors that are not Throwables, materialize via `toThrowable(conv)` with a
      // generic `RuntimeException` carrier — the typical SpecIdentity path errors with `E = Throwable`
      // anyway (the typed error channel is materialized from `Bifunctorized.bifunctorizeIdentity`'s
      // `MiniBIO.syncThrowable`), so this path is exercised only for non-standard E.
      case failure: Exit.FailureUninterrupted[E] => throw failure.toThrowable((e: E) => new RuntimeException(s"Typed error from MiniBIO: $e"))
    }

    override def unsafeRunSync[E, A](io: => MiniBIO[E, A]): Exit[E, A] = io.run()

    override def unsafeRunAsync[E, A](io: => MiniBIO[E, A])(callback: Exit[E, A] => Unit): Unit =
      callback(io.run())

    override def unsafeRunAsyncAsFuture[E, A](io: => MiniBIO[E, A]): Future[Exit[E, A]] =
      Future.successful(io.run())

    override def unsafeRunAsyncInterruptible[E, A](io: => MiniBIO[E, A])(callback: Exit[E, A] => Unit): InterruptAction[MiniBIO] = {
      callback(io.run())
      InterruptAction(MiniBIO.IOForMiniBIO.unit)
    }

    override def unsafeRunAsyncAsInterruptibleFuture[E, A](io: => MiniBIO[E, A]): (Future[Exit[E, A]], InterruptAction[MiniBIO]) = {
      val promise = Promise[Exit[E, A]]()
      promise.success(io.run())
      (promise.future, InterruptAction(MiniBIO.IOForMiniBIO.unit))
    }
  }

  /** Backing Primitives2 implementation for `IdentityBifunctorized`. Operates over MiniBIO. */
  private object PrimitivesForIdentityBifunctorized extends Primitives2[MiniBIO] {
    override def mkRef[A](a: A): MiniBIO[Nothing, Ref2[MiniBIO, A]] = MiniBIO.IOForMiniBIO.sync {
      val state = new AtomicReference[A](a)
      new Ref2[MiniBIO, A] {
        override def get: MiniBIO[Nothing, A] = MiniBIO.IOForMiniBIO.sync(state.get())
        override def set(a: A): MiniBIO[Nothing, Unit] = MiniBIO.IOForMiniBIO.sync(state.set(a))
        override def modify[B](f: A => (B, A)): MiniBIO[Nothing, B] = MiniBIO.IOForMiniBIO.sync {
          var out: B = null.asInstanceOf[B]
          state.updateAndGet {
            current =>
              val (b, next) = f(current)
              out = b
              next
          }
          out
        }
        override def update(f: A => A): MiniBIO[Nothing, A] = MiniBIO.IOForMiniBIO.sync(state.updateAndGet(f(_)))
        override def update_(f: A => A): MiniBIO[Nothing, Unit] = MiniBIO.IOForMiniBIO.sync { state.updateAndGet(f(_)); () }
        override def tryModify[B](f: A => (B, A)): MiniBIO[Nothing, Option[B]] = MiniBIO.IOForMiniBIO.sync {
          val cur = state.get()
          val (b, next) = f(cur)
          if (state.compareAndSet(cur, next)) Some(b) else None
        }
        override def tryUpdate(f: A => A): MiniBIO[Nothing, Option[A]] = MiniBIO.IOForMiniBIO.sync {
          val cur = state.get()
          val next = f(cur)
          if (state.compareAndSet(cur, next)) Some(next) else None
        }
      }
    }

    override def mkPromise[E, A]: MiniBIO[Nothing, Promise2[MiniBIO, E, A]] = MiniBIO.IOForMiniBIO.sync {
      val cell = new AtomicReference[Option[Either[E, A]]](None)
      new Promise2[MiniBIO, E, A] {
        override def await: MiniBIO[E, A] = MiniBIO.IOForMiniBIO.flatMap(MiniBIO.IOForMiniBIO.sync(cell.get())) {
          case Some(Right(a)) => MiniBIO.IOForMiniBIO.pure(a)
          case Some(Left(e)) => MiniBIO.IOForMiniBIO.fail(e)
          case None =>
            MiniBIO.IOForMiniBIO.terminate(new IllegalStateException("Promise2.await on unset promise (single-threaded MiniBIO carrier — there is no fiber to wait on)"))
        }
        override def poll: MiniBIO[Nothing, Option[MiniBIO[E, A]]] = MiniBIO.IOForMiniBIO.sync {
          cell.get().map {
            case Right(a) => MiniBIO.IOForMiniBIO.pure(a)
            case Left(e) => MiniBIO.IOForMiniBIO.fail(e)
          }
        }
        override def succeed(a: A): MiniBIO[Nothing, Boolean] = MiniBIO.IOForMiniBIO.sync(cell.compareAndSet(None, Some(Right(a))))
        override def fail(e: E): MiniBIO[Nothing, Boolean] = MiniBIO.IOForMiniBIO.sync(cell.compareAndSet(None, Some(Left(e))))
        override def terminate(t: Throwable): MiniBIO[Nothing, Boolean] = MiniBIO.IOForMiniBIO.sync {
          cell.compareAndSet(None, Some(Left(t.asInstanceOf[E])))
        }
      }
    }

    override def mkSemaphore(permits: Long): MiniBIO[Nothing, Semaphore2[MiniBIO]] = MiniBIO.IOForMiniBIO.sync {
      val counter = new java.util.concurrent.atomic.AtomicLong(permits)
      new Semaphore2[MiniBIO] {
        override def acquire: MiniBIO[Nothing, Unit] = acquireN(1L)
        override def release: MiniBIO[Nothing, Unit] = releaseN(1L)
        override def acquireN(n: Long): MiniBIO[Nothing, Unit] = MiniBIO.IOForMiniBIO.sync {
          if (counter.addAndGet(-n) < 0L) {
            counter.addAndGet(n)
            throw new IllegalStateException(
              s"Semaphore2.acquireN($n) under contention on a single-threaded MiniBIO carrier — there is no fiber to release the semaphore"
            )
          }
        }
        override def releaseN(n: Long): MiniBIO[Nothing, Unit] = MiniBIO.IOForMiniBIO.sync { counter.addAndGet(n); () }
        override def lifecycle: izumi.functional.lifecycle.Lifecycle[MiniBIO, Nothing, Unit] =
          izumi.functional.lifecycle.Lifecycle.make[MiniBIO, Nothing, Unit](acquire)(_ => release)
      }
    }
  }

}
