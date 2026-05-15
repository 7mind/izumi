package izumi.distage.testkit.runner.impl

import izumi.functional.bio.{Exit, IO2, UnsafeRun2, WeakAsync2}

import scala.concurrent.{Future, Promise}

trait RunnerToF[F[+_, +_]] {
  def runToF[G[+_, +_], E, A](runner: UnsafeRun2[G], f: () => G[E, A]): F[Throwable, A]
}

object RunnerToF extends RunnerToFPlatformSpecific {

  final class AsyncImpl[F[+_, +_]](
    F: IO2[F],
    FA: WeakAsync2[F],
  ) extends RunnerToF[F] {
    override def runToF[G[+_, +_], E, A](runner: UnsafeRun2[G], f: () => G[E, A]): F[Throwable, A] = {
      F.suspendThrowable {
        val (future, interrupt) = runner.unsafeRunAsyncAsInterruptibleFuture(f())
        // Re-throw failure exits in the F[Throwable, _] channel so that downstream `sandbox` machinery
        // can capture them as `Exit.FailureUninterrupted[Throwable]`.
        val exitToA: F[Throwable, A] = F.flatMap(FA.fromFuture(future)) {
          case Exit.Success(value) => F.pure(value)
          case failure: Exit.Failure[?] =>
            F.terminate(failure.trace.unsafeAttachTraceOrReturnNewThrowable())
        }
        // On interruption, run the G-side cancellation via the runner, fire-and-forget — we model
        // it as `FA.fromFuture(interruptToFuture())` so failures in the cancellation surface as
        // typed Throwable in F's channel.
        val interruptToFuture: () => Future[Unit] = () => {
          val p = Promise[Unit]()
          runner.unsafeRunAsync(interrupt.interrupt)(_ => p.success(()))
          p.future
        }
        F.guarantee(exitToA, F.orTerminate(F.void(FA.fromFuture(interruptToFuture()))))
      }
    }
  }

}
