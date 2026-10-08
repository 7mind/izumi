package izumi.distage.testkit.runner.di

import distage.TagK
import izumi.distage.testkit.runner.TestCancelled
import izumi.distage.testkit.runner.api.TestFinalizationReporter
import izumi.distage.testkit.runner.impl.services.TestConfigLoader
import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.{QuasiAsync, QuasiIO, QuasiIORunner}
import izumi.fundamentals.platform.functional.Identity

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

private[di] object OwnedFactoryFixtures {
  private final class OriginalFailure(message: String) extends RuntimeException(message, null, false, true)

  def run(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    Vector(false, true).foldLeft(Future.unit) { (before, fails) => before
      .flatMap(_ => exercise[MiniBIOAsync[Throwable, _]]("MiniBIO", TestRunnerRuntime.runnerLifecycleForMiniBIOAsync(), fails, context, verify))
      .flatMap(_ => exercise[cats.effect.IO]("Cats IO", TestRunnerRuntime.defaultRunnerLifecycleFor[cats.effect.IO], fails, context, verify))
      .flatMap(_ => exercise[zio.Task]("ZIO", TestRunnerRuntime.defaultRunnerLifecycleFor[zio.Task], fails, context, verify))
    }.map { _ => println("OWNED_FACTORY_CONTRACTS_OK cases=6") }
  }

  private def exercise[F[_]: TagK](
    label: String,
    delegate: Lifecycle[Identity, QuasiIORunner[F]],
    fails: Boolean,
    context: ExecutionContext,
    verify: (String, Boolean) => Unit,
  )(implicit F: QuasiIO[F], FA: QuasiAsync[F]): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val fixture = new OwnedRuntimeFixture(new OriginalFailure(label + " original runner graph release"), new OriginalFailure(label + " original outer allocation release"), fails)
    import fixture.*
    val recorded = new RecordingFinalization
    val factory = runtime(delegate)
    val preparation = factory.prepare(new PreparedRuntimeFixtures.EmptyReporter, new TestFinalizationReporter.Rethrowing, recorded,
      _.isInstanceOf[TestCancelled], Nil, new TestConfigLoader.TestConfigLoaderImpl, DistageRunnerOptions(false, false))
    preparation.flatMap { prepared =>
      val retained = outerAcquired.get() == 1 && graphAcquired.get() == 1 && outerReleased.get() == 0 && graphReleased.get() == 0
      val first = prepared.close()
      val second = prepared.close()
      releasing.future.flatMap { _ =>
        val held = !first.isCompleted && !second.isCompleted && graphReleased.get() == 0 && outerReleased.get() == 0
        val _ = release.success(())
        first.transform { result =>
          verify(label + " preparation retains the override graph and outer allocation", retained)
          verify(label + " inspection close joins held override finalization once", held && (first eq second) && graphReleased.get() == 1 && outerReleased.get() == 1)
          result match {
            case Success(_) => verify(label + " successful owned preparation releases without finalizer failures", !fails && recorded.failures.isEmpty)
            case Failure(cause) =>
              def contains(current: Throwable, original: Throwable): Boolean =
                (current eq original) || Option(current.getCause).exists(contains(_, original)) || current.getSuppressed.exists(contains(_, original))
              verify(label + " failed inspection close retains independent graph and outer throwables", fails && contains(cause, graphFailure) && contains(cause, outerFailure))
              verify(label + " runner graph observer receives the original finalizer once", recorded.failures.size == 1 && (recorded.failures.head eq graphFailure))
              verify(label + " failure aggregation leaves suppression-disabled originals unchanged", graphFailure.getCause == null && outerFailure.getCause == null && graphFailure.getSuppressed.isEmpty && outerFailure.getSuppressed.isEmpty)
          }
          Success(())
        }
      }
    }
  }

  private final class RecordingFinalization extends TestFinalizationReporter {
    private var observed = Vector.empty[Throwable]
    def failures: Vector[Throwable] = synchronized(observed)
    override def failure(cause: Throwable): Unit = synchronized { observed :+= cause }
  }
}
