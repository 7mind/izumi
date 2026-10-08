package izumi.distage.testkit.runner.di

import distage.{Locator, LocatorDef, LocatorRef, TagK}
import izumi.distage.testkit.runner.api.{TestFinalizationReporter, TestReporter}
import izumi.distage.testkit.runner.impl.{DistageTestRunner, RunnerToF, TestPlanner, TestTreeBuilder}
import izumi.distage.testkit.runner.impl.services.{ParTraverseExt, TestConfigLoader, TestkitLogging, TestResourceLifecycle, TestStatusConverter, TimedActionF}
import izumi.distage.testkit.runner.TestCancelled
import izumi.functional.quasi.{QuasiAsync, QuasiIO}
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.api.Log
import izumi.logstage.api.logger.{LogQueue, LogSink}

import java.util.concurrent.atomic.AtomicReference

final case class DistageRunnerOptions(debugOutput: Boolean, skipDockerFailures: Boolean)

private[distage] final class DistageEngine[F[_]: TagK](
  configLoader: TestConfigLoader,
  options: DistageRunnerOptions,
)(implicit F: QuasiIO[F], FA: QuasiAsync[F]) {
  def runner(reporter: TestReporter, finalization: TestFinalizationReporter): DistageTestRunner[F] = {
    val logging = new TestkitLogging {
      override def enableDebugOutput: Boolean = options.debugOutput
    }
    val converter = new TestStatusConverter(_.isInstanceOf[TestCancelled], options.skipDockerFailures)
    val parent = new LocatorDef {
      make[TagK[F]].fromValue(implicitly[TagK[F]])
      make[QuasiIO[F]].fromValue(F)
      make[QuasiAsync[F]].fromValue(FA)
      make[TestReporter].fromValue(reporter)
      make[TestFinalizationReporter].fromValue(finalization)
      make[TestkitLogging].fromValue(logging)
      make[TestStatusConverter].fromValue(converter)
    }
    val queue = new LogQueue {
      override def append(entry: Log.Entry, target: LogSink): Unit = target.flush(entry)
    }
    val planner = new TestPlanner(
      logging,
      configLoader,
      new TestTreeBuilder.TestTreeBuilderImpl(new TimedActionF.TimedActionFImpl[Identity]),
      new LocatorRef(new AtomicReference[Either[Locator, Locator]](Right(parent))),
      queue,
    )
    new DistageTestRunner[F](
      reporter,
      logging,
      planner,
      converter,
      new TimedActionF.TimedActionFImpl[F],
      new TestResourceLifecycle[F](finalization),
      new RunnerToF.AsyncImpl[F](F, FA),
      new ParTraverseExt.ParTraverseExtImpl[F],
    )
  }
}
