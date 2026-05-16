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
  * JS-specific stub: `unsafeRunSync` throws because the JS event loop cannot block the calling
  * thread. The async methods (`unsafeRunAsync*`) work correctly via `IORuntime`. This matches the
  * existing `CatsIOPlatformDependentTest` JS limitation ("without a working unsafeRunSync we can't
  * run cats support tests on JS").
  *
  * Typed errors submerged into `IO`'s Throwable channel as [[SubmergedTypedError]] are
  * re-projected back to [[Exit.Error]] on the async path; defects pass through as
  * [[Exit.Termination]].
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
        throw new UnsupportedOperationException(
          "UnsafeRun2[Bifunctorized[cats.effect.IO, +_, +_]].unsafeRun is JVM-only: the JS event loop cannot block the calling thread. Use unsafeRunAsync* methods instead."
        )
      }

      override def unsafeRunSync[E, A](io: => Bifunctorized[IO, E, A]): Exit[E, A] = {
        throw new UnsupportedOperationException(
          "UnsafeRun2[Bifunctorized[cats.effect.IO, +_, +_]].unsafeRunSync is JVM-only: the JS event loop cannot block the calling thread. Use unsafeRunAsync* methods instead."
        )
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
    * JS-specific stub: synchronous `unsafeRun*` methods throw because the JS event loop cannot
    * block. The async methods route through `Dispatcher.unsafeToFutureCancelable`. Matches the
    * existing platform limitation on cats-effect IO under Scala.js.
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
        throw new UnsupportedOperationException(
          "UnsafeRun2[Bifunctorized[F, +_, +_]].unsafeRun via Dispatcher is JVM-only: Scala.js has no Dispatcher.unsafeRunSync. Use unsafeRunAsync* methods instead."
        )
      }

      override def unsafeRunSync[E, A](io: => Bifunctorized[F, E, A]): Exit[E, A] = {
        throw new UnsupportedOperationException(
          "UnsafeRun2[Bifunctorized[F, +_, +_]].unsafeRunSync via Dispatcher is JVM-only: Scala.js has no Dispatcher.unsafeRunSync. Use unsafeRunAsync* methods instead."
        )
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
