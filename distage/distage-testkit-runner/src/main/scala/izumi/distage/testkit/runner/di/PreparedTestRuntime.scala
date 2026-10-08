package izumi.distage.testkit.runner.di

import izumi.distage.testkit.runner.{Cancellation, OnceFuture}
import izumi.distage.testkit.model.{DistageTest, EnvResult}
import izumi.distage.testkit.runner.impl.DistageTestRunner
import izumi.distage.testkit.runner.impl.TestPlanner.PlannedTests
import izumi.distage.testkit.runner.impl.services.Timed
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.{QuasiAsync, QuasiIO}
import izumi.fundamentals.platform.language.types.HigherKindedAny.AnyF

import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

private[di] trait PreparedTestRuntime {
  def planned: Timed[PlannedTests[AnyF]]
  def execute(): RuntimeExecution[List[EnvResult]]
  def failures(cause: Throwable, cancelled: Boolean): Vector[Throwable]
  def stop(): Future[Unit]
  def close(): Future[Unit]
}

private[di] object PreparedTestRuntime {
  private sealed trait Decision
  private case object Execute extends Decision
  private case object Close extends Decision

  def acquire[F[_]](
    runtime: TestRuntime[F],
    runnerResource: Lifecycle[F, DistageTestRunner[F]],
    tests: Seq[DistageTest[AnyF]],
    completionContext: ExecutionContext,
  )(implicit F: QuasiIO[F], FA: QuasiAsync[F]): Future[PreparedTestRuntime] = acquire(runtime, runnerResource, tests, completionContext, new Cancellation)

  def acquire[F[_]](
    runtime: TestRuntime[F],
    runnerResource: Lifecycle[F, DistageTestRunner[F]],
    tests: Seq[DistageTest[AnyF]],
    completionContext: ExecutionContext,
    cancellation: Cancellation,
  )(implicit F: QuasiIO[F], FA: QuasiAsync[F]): Future[PreparedTestRuntime] = {
    val start = Promise[Unit]()
    val ready = Promise[DistageTestRunner.PreparedRun[F]]()
    val decision = Promise[Decision]()
    val interrupted = new AtomicBoolean(false)
    val projectedInterruption = new AtomicReference[Throwable]()
    val observedFailures = new AtomicReference(Vector.empty[Throwable])
    val effect = F.guaranteeOnInterrupt(F.definitelyRecoverWithTrace {
      F.flatMap(FA.fromFuture(start.future)) { _ => runnerResource.use { runner =>
        F.flatMap(runner.plan(tests)) { prepared =>
          F.flatMap(F.maybeSuspend { val _ = ready.success(prepared) }) { _ =>
            F.flatMap(FA.fromFuture(decision.future)) {
              case Execute => runner.runPrepared(prepared)
              case Close => F.pure(List.empty[EnvResult])
            }
          }
        }
      }
      }
    } { (cause, _) =>
      F.flatMap(F.maybeSuspend { observedFailures.set(observedFailures.get() :+ cause) })(_ => F.fail[List[EnvResult]](cause))
    })(_ => F.maybeSuspend { interrupted.set(true) })
    val operation = runtime.start { runner =>
      val (execution, stop) = runner.runFutureInterruptible(effect)
      val observed = execution.transform { result =>
        if (interrupted.get()) result.failed.foreach(projectedInterruption.set)
        result
      }(completionContext)
      (observed, stop)
    }
    val registration = cancellation.onRequest(() => if (ready.isCompleted) Future.unit else operation.stop())
    val _ = start.trySuccess(())
    operation.completion.onComplete {
      case Failure(cause) => val _ = ready.tryFailure(cause)
      case Success(_) => val _ = ready.tryFailure(new IllegalStateException("Runner completed without publishing its prepared plan"))
    }(completionContext)
    ready.future.transformWith { result =>
      registration.close().transformWith(_ => Future.fromTry(result))(completionContext)
    }(completionContext).map { prepared =>
      new PreparedTestRuntime {
        override val planned: Timed[PlannedTests[AnyF]] = prepared.planned
        private val closing = new OnceFuture[Unit](this)

        override def execute(): RuntimeExecution[List[EnvResult]] = {
          require(decision.trySuccess(Execute), "Prepared runner already has an execution or close decision")
          operation
        }

        override def failures(cause: Throwable, cancelled: Boolean): Vector[Throwable] = {
          def originals(current: Throwable): Vector[Throwable] = current match {
            case aggregate: TestRuntime.RuntimeCompletionException => originals(aggregate.primary) ++ aggregate.additional.toVector.flatMap(originals)
            case original => Vector(original)
          }
          val retained = originals(cause).flatMap { original =>
            if (cancelled && interrupted.get()) original match {
              case expected if expected eq projectedInterruption.get() => Vector.empty
              case boxed: ExecutionException if boxed.getCause eq projectedInterruption.get() => boxed.getSuppressed.toVector
              case independent => Vector(independent)
            } else Vector(original)
          }
          if (cancelled && interrupted.get()) retained ++ observedFailures.get().filterNot(retained.contains)
          else retained
        }

        override def stop(): Future[Unit] = operation.stop()

        override def close(): Future[Unit] = closing(()) { _ =>
          if (!decision.trySuccess(Close)) { val _ = operation.stop() }
          operation.completion.map(_ => ())(completionContext)
        }
      }
    }(completionContext)
  }
}
