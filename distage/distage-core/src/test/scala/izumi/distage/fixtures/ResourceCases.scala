package izumi.distage.fixtures

import java.util.concurrent.atomic.AtomicReference
import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{Bifunctorized, Exit, IO2, Primitives2, Promise2, Ref1, Ref2, Semaphore2}
import izumi.functional.bio.data.{Morphism2, RestoreInterruption2}
import izumi.fundamentals.platform.language.Quirks.*

import scala.collection.immutable.Queue
import scala.collection.mutable
import scala.util.Try

object ResourceCases {

  object ResourceCase1 {
    sealed trait Ops
    case object XStart extends Ops
    case object XStop extends Ops
    case object YStart extends Ops
    case object YStop extends Ops
    case object RStart extends Ops
    case object RStop extends Ops
    case object ZStart extends Ops
    case object ZStop extends Ops

    class X
    class Y
    class Z

    val queueEffect: Suspend2[Nothing, mutable.Queue[Ops]] = Suspend2(mutable.Queue.empty[Ops])

    class XResource(queue: mutable.Queue[Ops]) extends Lifecycle.Basic[Suspend2, Nothing, X] {
      override def acquire: Suspend2[Nothing, X] = Suspend2 {
        queue += XStart
        new X
      }

      override def release(resource: X): Suspend2[Nothing, Unit] = Suspend2 {
        resource.discard()

        queue += XStop
      }.void
    }

    class YResource(x: X, queue: mutable.Queue[Ops]) extends Lifecycle.Basic[Suspend2, Nothing, Y] {
      x.discard()

      override def acquire: Suspend2[Nothing, Y] = Suspend2 {
        queue += YStart
        new Y
      }

      override def release(resource: Y): Suspend2[Nothing, Unit] = Suspend2 {
        resource.discard()

        queue += YStop
      }.void
    }

    class ZFaultyResource(y: Y) extends Lifecycle.Basic[Suspend2, Throwable, Z] {
      y.discard()

      override def acquire: Suspend2[Throwable, Z] = throw new RuntimeException()
      override def release(resource: Z): Suspend2[Nothing, Unit] = throw new RuntimeException()
    }
  }

  object CircularResourceCase {

    sealed trait Ops { def invert: Ops }
    case object ComponentStart extends Ops { val invert: Ops = ComponentStop }
    case object ClientStart extends Ops { val invert: Ops = ClientStop }
    case object ComponentStop extends Ops { val invert: Ops = ComponentStart }
    case object ClientStop extends Ops { val invert: Ops = ClientStart }

    trait S3Client {
      def c: S3Component
    }
    trait IntegrationComponent

    class S3Component(val s: S3Client) extends IntegrationComponent
    class S3ClientImpl(val c: S3Component) extends S3Client

    def s3ComponentResource[F[+_, +_]: IO2](ref: Ref[F, Queue[Ops]], s3Client: S3Client): Lifecycle[F, Nothing, S3Component] =
      Lifecycle.make[F, Nothing, S3Component](
        acquire = IO2[F].map(ref.update(_ :+ ComponentStart))(_ => new S3Component(s3Client))
      )(release = _ => ref.update_(_ :+ ComponentStop))

    def s3clientResource[F[+_, +_]: IO2](ref: Ref[F, Queue[Ops]], s3Component: S3Component): Lifecycle[F, Nothing, S3ClientImpl] =
      Lifecycle.make[F, Nothing, S3ClientImpl](
        acquire = IO2[F].map(ref.update(_ :+ ClientStart))(_ => new S3ClientImpl(s3Component))
      )(release = _ => ref.update_(_ :+ ClientStop))

  }

  object ClassResourceCase {

    class Res {
      var initialized: Boolean = false
    }

    class SimpleResource extends Lifecycle.Basic[Bifunctorized.IdentityBifunctorized, Throwable, Res] {
      override def acquire: Bifunctorized.IdentityBifunctorized[Throwable, Res] = Bifunctorized.bifunctorizeIdentity {
        val x = new Res; x.initialized = true; x
      }

      override def release(resource: Res): Bifunctorized.IdentityBifunctorized[Nothing, Unit] =
        Bifunctorized.bifunctorizeIdentity(resource.initialized = false).asInstanceOf[Bifunctorized.IdentityBifunctorized[Nothing, Unit]]
    }

    class SuspendResource extends Lifecycle.Basic[Suspend2, Nothing, Res] {
      override def acquire: Suspend2[Nothing, Res] = Suspend2(new Res).flatMap(r => Suspend2(r.initialized = true).map(_ => r))

      override def release(resource: Res): Suspend2[Nothing, Unit] = Suspend2(resource.initialized = false)
    }

  }

  class MutResource extends Lifecycle.Self[Bifunctorized.IdentityBifunctorized, Throwable, MutResource] { this: MutResource =>
    var init: Boolean = false
    def acquire: Bifunctorized.IdentityBifunctorized[Throwable, Unit] = Bifunctorized.bifunctorizeIdentity { init = true }
    def release: Bifunctorized.IdentityBifunctorized[Nothing, Unit] =
      Bifunctorized.bifunctorizeIdentity(()).asInstanceOf[Bifunctorized.IdentityBifunctorized[Nothing, Unit]]
  }

  class Ref[F[+_, +_], A](r: AtomicReference[A])(implicit F: IO2[F]) {
    def get: F[Nothing, A] = F.sync(r.get())
    def update(f: A => A): F[Nothing, A] = F.sync(r.synchronized { r.set(f(r.get())); r.get() }) // no `.updateAndGet` on scala.js...
    def update_(f: A => A): F[Nothing, Unit] = F.void(update(f))
    def set(a: A): F[Nothing, A] = update(_ => a)
  }

  object Ref {
    def apply[F[+_, +_]]: Apply[F] = new Apply[F]()

    final class Apply[F[+_, +_]](private val dummy: Boolean = false) extends AnyVal {
      def apply[A](a: A)(implicit F: IO2[F]): F[Nothing, Ref[F, A]] = {
        F.sync(new Ref[F, A](new AtomicReference(a)))
      }
    }
  }

  case class Suspend2[+E, +A](run: () => Either[E, A]) {
    def map[B](g: A => B): Suspend2[E, B] = {
      Suspend2(() => run().map(g))
    }
    def flatMap[E1 >: E, B](g: A => Suspend2[E1, B]): Suspend2[E1, B] = {
      Suspend2(() => run().flatMap(g(_).run()))
    }
    def void: Suspend2[E, Unit] = map(_ => ())

    def unsafeRun(): A = run() match {
      case Left(value: Throwable) => throw value
      case Left(value) => throw new RuntimeException(value.toString)
      case Right(value) => value
    }
  }
  object Suspend2 {
    def apply[A](a: => A)(implicit dummy: DummyImplicit): Suspend2[Nothing, A] = new Suspend2(() => Right(a))

    implicit val IO2Suspend2: IO2[Suspend2] = new IO2[Suspend2] {
      override def flatMap[E, A, B](r: Suspend2[E, A])(f: A => Suspend2[E, B]): Suspend2[E, B] = r.flatMap(f)
      override def map[E, A, B](r: Suspend2[E, A])(f: A => B): Suspend2[E, B] = r.map(f)
      override def pure[A](a: A): Suspend2[Nothing, A] = Suspend2(a)
      override def fail[E](v: => E): Suspend2[E, Nothing] = Suspend2(() => Left(v))
      override def terminate(v: => Throwable): Suspend2[Nothing, Nothing] = Suspend2(() => throw v)
      override def sendInterruptToSelf: Suspend2[Nothing, Unit] = Suspend2(())

      override def syncThrowable[A](effect: => A): Suspend2[Throwable, A] = {
        Suspend2 {
          () =>
            Try(effect).toEither match {
              case Left(t) => Left(t)
              case Right(v) => Right(v)
            }
        }
      }
      override def sync[A](effect: => A): Suspend2[Nothing, A] = Suspend2(() => Right(effect))

      override def redeem[E, A, E2, B](r: Suspend2[E, A])(err: E => Suspend2[E2, B], succ: A => Suspend2[E2, B]): Suspend2[E2, B] = {
        // Catch defects (Throwables) raised during r.run() and route via `err` (treating
        // Throwable as the typed error). This matches pre-bifunctorization
        // QuasiIO[Identity].redeem(action)(failure, success) semantics where the failure
        // path was invoked for both typed errors AND synchronously thrown exceptions.
        new Suspend2[E2, B](() => {
          val attempt: Either[E, A] =
            try r.run()
            catch { case t: Throwable => Left(t.asInstanceOf[E]) }
          attempt match {
            case Left(error) => err(error).run()
            case Right(value) => succ(value).run()
          }
        })
      }
      override def catchAll[E, A, E2](r: Suspend2[E, A])(f: E => Suspend2[E2, A]): Suspend2[E2, A] = redeem(r)(f, pure)

      override def bracketCase[E, A, B](
        acquire: Suspend2[E, A]
      )(release: (A, Exit[E, B]) => Suspend2[Nothing, Unit]
      )(use: A => Suspend2[E, B]
      ): Suspend2[E, B] = {
        acquire.flatMap {
          a =>
            new Suspend2[E, B](() => {
              // Catch Throwable defects raised by `use(a).run()` (e.g. user lambda that throws
              // synchronously, like `flatMap(_ => throw)`) and route them through release;
              // otherwise defects skip cleanup entirely. Typed errors continue through redeem.
              val outcome: Either[E, B] =
                try use(a).run()
                catch {
                  case t: Throwable =>
                    // Run release on defect path then re-raise (we lack a defect channel in
                    // Suspend2, so the unrecoverable Throwable continues to escape `unsafeRun`).
                    try release(a, Exit.Termination(t, Exit.Trace.forUnknownError)).run() catch { case _: Throwable => () }
                    throw t
                }
              outcome match {
                case Right(v) =>
                  release(a, Exit.Success(v)).run()
                  Right(v)
                case Left(err) =>
                  release(a, Exit.Error(err, Exit.Trace.forUnknownError)).run()
                  Left(err)
              }
            })
        }
      }

      override def sandbox[E, A](r: Suspend2[E, A]): Suspend2[Exit.FailureUninterrupted[E], A] = {
        Suspend2(
          () =>
            r.run() match {
              case Left(value) => Left(Exit.Error(value, Exit.Trace.forUnknownError))
              case Right(value) => Right(value)
            }
        )
      }

      override def uninterruptible[E, A](f: Suspend2[E, A]): Suspend2[E, A] = f
      override def uninterruptibleExcept[E, A](f: RestoreInterruption2[Suspend2] => Suspend2[E, A]): Suspend2[E, A] = f(Morphism2.identity[Suspend2])
      override def bracketExcept[E, A, B](
        acquire: RestoreInterruption2[Suspend2] => Suspend2[E, A]
      )(release: (A, Exit[E, B]) => Suspend2[Nothing, Unit]
      )(use: A => Suspend2[E, B]
      ): Suspend2[E, B] = {
        bracketCase[E, A, B](acquire(Morphism2.identity[Suspend2]))(release)(use)
      }
    }

    /** Synchronous in-memory primitives for `Suspend2`.
      * Mirrors [[izumi.functional.bio.BifunctorizedNoOpInstances]] for the bifunctor-shaped
      * identity-style carrier: `mkRef` is exact; `mkPromise.await` and
      * `mkSemaphore.acquire` fail under contention (single-threaded carrier — there is
      * no fiber to wait on).
      */
    implicit val Primitives2Suspend2: Primitives2[Suspend2] = new Primitives2[Suspend2] {
      override def mkRef[A](a: A): Suspend2[Nothing, Ref2[Suspend2, A]] = Suspend2 {
        val state = new AtomicReference[A](a)
        val ref: Ref2[Suspend2, A] = new Ref1[Suspend2[Nothing, _], A] {
          override def get: Suspend2[Nothing, A] = Suspend2(state.get())
          override def set(a: A): Suspend2[Nothing, Unit] = Suspend2(state.set(a))
          override def modify[B](f: A => (B, A)): Suspend2[Nothing, B] = Suspend2 {
            var out: B = null.asInstanceOf[B]
            state.updateAndGet { current =>
              val (b, next) = f(current)
              out = b
              next
            }
            out
          }
          override def update(f: A => A): Suspend2[Nothing, A] = Suspend2(state.updateAndGet(f(_)))
          override def update_(f: A => A): Suspend2[Nothing, Unit] = Suspend2 { state.updateAndGet(f(_)); () }
          override def tryModify[B](f: A => (B, A)): Suspend2[Nothing, Option[B]] = Suspend2 {
            val cur = state.get()
            val (b, next) = f(cur)
            if (state.compareAndSet(cur, next)) Some(b) else None
          }
          override def tryUpdate(f: A => A): Suspend2[Nothing, Option[A]] = Suspend2 {
            val cur = state.get()
            val next = f(cur)
            if (state.compareAndSet(cur, next)) Some(next) else None
          }
        }
        ref
      }

      override def mkPromise[E, A]: Suspend2[Nothing, Promise2[Suspend2, E, A]] = Suspend2 {
        val cell = new AtomicReference[Option[Either[E, A]]](None)
        new Promise2[Suspend2, E, A] {
          override def await: Suspend2[E, A] = Suspend2(cell.get()).flatMap[E, A] {
            case Some(Right(a)) => Suspend2[A](a)
            case Some(Left(e)) => new Suspend2[E, A](() => Left(e))
            case None => Suspend2[A](throw new IllegalStateException("Promise2.await on unset promise (single-threaded Suspend2 carrier — there is no fiber to wait on)"))
          }
          override def poll: Suspend2[Nothing, Option[Suspend2[E, A]]] = Suspend2 {
            cell.get().map {
              case Right(a) => Suspend2[A](a): Suspend2[E, A]
              case Left(e) => new Suspend2[E, A](() => Left(e))
            }
          }
          override def succeed(a: A): Suspend2[Nothing, Boolean] = Suspend2(cell.compareAndSet(None, Some(Right(a))))
          override def fail(e: E): Suspend2[Nothing, Boolean] = Suspend2(cell.compareAndSet(None, Some(Left(e))))
          override def terminate(t: Throwable): Suspend2[Nothing, Boolean] = Suspend2(cell.compareAndSet(None, Some(Left(t.asInstanceOf[E]))))
        }
      }

      override def mkSemaphore(permits: Long): Suspend2[Nothing, Semaphore2[Suspend2]] = Suspend2 {
        val counter = new java.util.concurrent.atomic.AtomicLong(permits)
        new Semaphore2[Suspend2] {
          override def acquire: Suspend2[Nothing, Unit] = acquireN(1L)
          override def release: Suspend2[Nothing, Unit] = releaseN(1L)
          override def acquireN(n: Long): Suspend2[Nothing, Unit] = Suspend2 {
            if (counter.addAndGet(-n) < 0L) {
              counter.addAndGet(n)
              throw new IllegalStateException(
                s"Semaphore2.acquireN($n) under contention on a single-threaded Suspend2 carrier — there is no fiber to release the semaphore"
              )
            }
          }
          override def releaseN(n: Long): Suspend2[Nothing, Unit] = Suspend2 { counter.addAndGet(n); () }
          override def lifecycle: izumi.functional.lifecycle.Lifecycle[Suspend2, Nothing, Unit] =
            izumi.functional.lifecycle.Lifecycle.make[Suspend2, Nothing, Unit](acquire)(_ => release)
        }
      }
    }
  }
}
