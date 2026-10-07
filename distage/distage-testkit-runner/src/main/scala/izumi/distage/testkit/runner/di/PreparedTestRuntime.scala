package izumi.distage.testkit.runner.di

import izumi.distage.testkit.model.{DistageTest, EnvResult}
import izumi.distage.testkit.runner.impl.DistageTestRunner
import izumi.distage.testkit.runner.impl.TestPlanner.PlannedTests
import izumi.distage.testkit.runner.impl.services.Timed
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.{QuasiAsync, QuasiIO}
import izumi.fundamentals.platform.language.types.HigherKindedAny.AnyF

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

private[di] trait PreparedTestRuntime {
  def planned: Timed[PlannedTests[AnyF]]
  def execute(): RuntimeExecution[List[EnvResult]]
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
  )(implicit F: QuasiIO[F], FA: QuasiAsync[F]): Future[PreparedTestRuntime] = {
    val ready = Promise[DistageTestRunner.PreparedRun[F]]()
    val decision = Promise[Decision]()
    val operation = runtime.run {
      runnerResource.use { runner =>
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
    operation.completion.onComplete {
      case Failure(cause) => val _ = ready.tryFailure(cause)
      case Success(_) => val _ = ready.tryFailure(new IllegalStateException("Runner completed without publishing its prepared plan"))
    }(completionContext)
    ready.future.map { prepared =>
      new PreparedTestRuntime {
        override val planned: Timed[PlannedTests[AnyF]] = prepared.planned
        private var closing = Option.empty[Promise[Unit]]

        override def execute(): RuntimeExecution[List[EnvResult]] = {
          require(decision.trySuccess(Execute), "Prepared runner already has an execution or close decision")
          operation
        }

        override def close(): Future[Unit] = {
          val (completion, admitted) = synchronized {
            closing match {
              case Some(previous) => (previous.future, None)
              case None =>
                val requested = Promise[Unit]()
                closing = Some(requested)
                (requested.future, Some(requested))
            }
          }
          admitted.foreach { requested =>
            if (!decision.trySuccess(Close)) { val _ = operation.stop() }
            val _ = requested.completeWith(operation.completion.map(_ => ())(completionContext))
          }
          completion
        }
      }
    }(completionContext)
  }
}
