package izumi.functional.bio

import izumi.functional.bio.PredefinedHelper.Predefined
import izumi.functional.bio.impl.MiniBIO

import java.util.concurrent.atomic.AtomicReference

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

  /** Backing Primitives2 implementation for `IdentityBifunctorized`. Operates over MiniBIO. */
  private object PrimitivesForIdentityBifunctorized extends Primitives2[MiniBIO] {
    override def mkRef[A](a: A): MiniBIO[Nothing, Ref2[MiniBIO, A]] = MiniBIO.IOForMiniBIO.sync {
      val state = new AtomicReference[A](a)
      new Ref2[MiniBIO, A] {
        override def get: MiniBIO[Nothing, A] = MiniBIO.IOForMiniBIO.sync(state.get())
        override def set(a: A): MiniBIO[Nothing, Unit] = MiniBIO.IOForMiniBIO.sync(state.set(a))
        override def modify[B](f: A => (B, A)): MiniBIO[Nothing, B] = MiniBIO.IOForMiniBIO.sync {
          var out: B = null.asInstanceOf[B]
          state.updateAndGet { current =>
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
          case None => MiniBIO.IOForMiniBIO.terminate(new IllegalStateException("Promise2.await on unset promise (single-threaded MiniBIO carrier — there is no fiber to wait on)"))
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
