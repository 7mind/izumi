package izumi.functional.bio.impl

import cats.effect.IO
import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import cats.effect.unsafe.IORuntime
import izumi.functional.bio.data.InterruptAction
import izumi.functional.bio.{Bifunctorized, Exit, SubmergedTypedError, UnsafeRun2}
import izumi.reflect.TagK

import scala.concurrent.{ExecutionContext, Future, Promise}

/** Build an [[UnsafeRun2]] for `Bifunctorized[cats.effect.IO, +_, +_]` from a `cats.effect.unsafe.IORuntime`.
  *
  * JVM-specific: `unsafeRunSync` blocks the calling thread on the underlying `IO`'s
  * completion. The async methods (`unsafeRunAsync*`) are cross-platform-shaped but live
  * in the JVM tree to mirror the platform-specific structure of the rest of the
  * cats-effect IO support.
  *
  * Typed errors submerged into `IO`'s Throwable channel as [[SubmergedTypedError]] are
  * re-projected back to [[Exit.Error]]; defects pass through as [[Exit.Termination]].
  */
object CatsIORunnerPlatformSpecific {

  def fromIORuntime(implicit IOR: IORuntime, tag: TagK[IO]): UnsafeRun2[Bifunctorized[IO, +_, +_]] = {
    new UnsafeRun2[Bifunctorized[IO, +_, +_]] {
      private[this] def throwableToExit[E](t: Throwable): Exit.FailureUninterrupted[E] = t match {
        case SubmergedTypedError(payload) =>
          Exit.Error(payload.asInstanceOf[E], Exit.Trace.ThrowableTrace(t))
        case other =>
          Exit.Termination(other, Exit.Trace.ThrowableTrace(other))
      }

      override def unsafeRun[E, A](io: => Bifunctorized[IO, E, A]): A = {
        io.asInstanceOf[IO[A]].unsafeRunSync()(IOR)
      }

      override def unsafeRunSync[E, A](io: => Bifunctorized[IO, E, A]): Exit[E, A] = {
        try Exit.Success(io.asInstanceOf[IO[A]].unsafeRunSync()(IOR))
        catch {
          case _: InterruptedException =>
            Exit.Interruption(Nil, Exit.Trace.forUnknownError)
          case t: Throwable =>
            throwableToExit[E](t)
        }
      }

      override def unsafeRunAsync[E, A](io: => Bifunctorized[IO, E, A])(callback: Exit[E, A] => Unit): Unit = {
        io.asInstanceOf[IO[A]].unsafeRunAsync {
          case Right(a) => callback(Exit.Success(a))
          case Left(t) => callback(throwableToExit[E](t))
        }(IOR)
      }

      override def unsafeRunAsyncAsFuture[E, A](io: => Bifunctorized[IO, E, A]): Future[Exit[E, A]] = {
        val p = Promise[Exit[E, A]]()
        unsafeRunAsync(io)(p.success)
        p.future
      }

      override def unsafeRunAsyncInterruptible[E, A](io: => Bifunctorized[IO, E, A])(callback: Exit[E, A] => Unit): InterruptAction[Bifunctorized[IO, +_, +_]] = {
        val (fut, cancel) = io.asInstanceOf[IO[A]].unsafeToFutureCancelable()(IOR)
        fut.onComplete {
          case scala.util.Success(a) => callback(Exit.Success(a))
          case scala.util.Failure(t) => callback(throwableToExit[E](t))
        }(ExecutionContext.parasitic)
        // `cancel` returns `Future[Unit]`; wrap it as an IO suspension so InterruptAction carries
        // an effect that triggers the dispatcher-level cancellation when run.
        InterruptAction(Bifunctorized.assert[IO, Nothing, Unit](IO.fromFuture(IO.delay(cancel()))))
      }

      override def unsafeRunAsyncAsInterruptibleFuture[E, A](io: => Bifunctorized[IO, E, A]): (Future[Exit[E, A]], InterruptAction[Bifunctorized[IO, +_, +_]]) = {
        val p = Promise[Exit[E, A]]()
        val interrupt = unsafeRunAsyncInterruptible(io)(p.success)
        (p.future, interrupt)
      }
    }
  }

  /** Build an [[UnsafeRun2]] for any `Bifunctorized[F, +_, +_]` from a `cats.effect.std.Dispatcher[F]`.
    *
    * JVM-only: relies on `Dispatcher.unsafeRunSync` for the synchronous-run methods, which is
    * not available on Scala.js (no thread blocking primitive). Use this when integrating an
    * arbitrary `cats.effect.kernel.Async[F]` effect type (e.g. a Tofu, monix-bio, IO-like
    * wrapper) into the distage runtime; provide a `Dispatcher[F]` via
    * `Dispatcher.parallel[F].use { implicit d => … }` at the call site.
    *
    * The lifetime of the resulting runner is bound to the `Dispatcher`'s lifecycle: closing the
    * dispatcher invalidates the runner.
    */
  def dispatcherToUnsafeRun2[F[_]](implicit F: Async[F], D: Dispatcher[F], tag: TagK[F]): UnsafeRun2[Bifunctorized[F, +_, +_]] = {
    new UnsafeRun2[Bifunctorized[F, +_, +_]] {
      private[this] def throwableToExit[E](t: Throwable): Exit.FailureUninterrupted[E] = t match {
        case SubmergedTypedError(payload) =>
          Exit.Error(payload.asInstanceOf[E], Exit.Trace.ThrowableTrace(t))
        case other =>
          Exit.Termination(other, Exit.Trace.ThrowableTrace(other))
      }

      override def unsafeRun[E, A](io: => Bifunctorized[F, E, A]): A = {
        // Dispatcher.unsafeRunSync rethrows the wrapped exception (SubmergedTypedError for
        // typed errors, raw Throwable for defects) — preserving the contract: typed errors
        // surface as `RuntimeException` (the SubmergedTypedError wrapper), defects unchanged.
        D.unsafeRunSync(io.asInstanceOf[F[A]])
      }

      override def unsafeRunSync[E, A](io: => Bifunctorized[F, E, A]): Exit[E, A] = {
        try Exit.Success(D.unsafeRunSync(io.asInstanceOf[F[A]]))
        catch {
          case _: InterruptedException =>
            Exit.Interruption(Nil, Exit.Trace.forUnknownError)
          case t: Throwable =>
            throwableToExit[E](t)
        }
      }

      override def unsafeRunAsync[E, A](io: => Bifunctorized[F, E, A])(callback: Exit[E, A] => Unit): Unit = {
        val (fut, _) = D.unsafeToFutureCancelable(io.asInstanceOf[F[A]])
        fut.onComplete {
          case scala.util.Success(a) => callback(Exit.Success(a))
          case scala.util.Failure(t) => callback(throwableToExit[E](t))
        }(ExecutionContext.parasitic)
      }

      override def unsafeRunAsyncAsFuture[E, A](io: => Bifunctorized[F, E, A]): Future[Exit[E, A]] = {
        val p = Promise[Exit[E, A]]()
        unsafeRunAsync(io)(p.success)
        p.future
      }

      override def unsafeRunAsyncInterruptible[E, A](io: => Bifunctorized[F, E, A])(callback: Exit[E, A] => Unit): InterruptAction[Bifunctorized[F, +_, +_]] = {
        val (fut, cancel) = D.unsafeToFutureCancelable(io.asInstanceOf[F[A]])
        fut.onComplete {
          case scala.util.Success(a) => callback(Exit.Success(a))
          case scala.util.Failure(t) => callback(throwableToExit[E](t))
        }(ExecutionContext.parasitic)
        InterruptAction(Bifunctorized.assert[F, Nothing, Unit](F.fromFuture(F.delay(cancel()))))
      }

      override def unsafeRunAsyncAsInterruptibleFuture[E, A](io: => Bifunctorized[F, E, A]): (Future[Exit[E, A]], InterruptAction[Bifunctorized[F, +_, +_]]) = {
        val p = Promise[Exit[E, A]]()
        val interrupt = unsafeRunAsyncInterruptible(io)(p.success)
        (p.future, interrupt)
      }
    }
  }

}
