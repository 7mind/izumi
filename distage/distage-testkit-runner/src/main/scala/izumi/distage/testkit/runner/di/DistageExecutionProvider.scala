package izumi.distage.testkit.runner.di

import izumi.distage.testkit.model.{DistageTest, TestMeta}
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*
import izumi.distage.testkit.runner.impl.services.TestConfigLoader
import izumi.distage.testkit.spec.{SessionPluginLoader, SessionTestEnvironment, TestEnvironmentFactory}
import izumi.distage.plugins.load.PluginLoaderDefaultImpl
import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.quasi.QuasiIORunner
import izumi.fundamentals.platform.language.types.HigherKindedAny.AnyF

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

private[distage] final case class ResolvedDistageTest(descriptor: TestDescriptor, test: DistageTest[AnyF])
private[distage] final case class RegisteredDistageTest(descriptor: TestDescriptor, resolve: RunOverrides => Either[Failure, ResolvedDistageTest])

final class DistageExecutionProvider(
  executionContext: ExecutionContext,
  configLoader: TestConfigLoader,
  options: DistageRunnerOptions,
) extends ExecutionProvider {
  private[distage] val environments = new SessionTestEnvironment(new SessionEnvironmentFactory(new TestEnvironmentFactory.Impl, new SessionBootstrapFactory))
  private[distage] val defaultPluginLoader = new SessionPluginLoader(cache => PluginLoaderDefaultImpl.withPackageCache(cache))
  private type RunnerF[A] = MiniBIOAsync[Throwable, A]
  private val engine = new DistageEngine[RunnerF](configLoader, options)
  private val effectRunner = QuasiIORunner.fromBIO[MiniBIOAsync](using MiniBIOAsync.UnsafeRunMiniBIOAsync(using executionContext))
  private var registrations = Vector.empty[RegisteredDistageTest]
  private var resolutions = Map.empty[TestDescriptor, DistageTest[AnyF]]
  private var planned = false

  private[distage] def add(tests: Vector[RegisteredDistageTest]): Unit = synchronized {
    require(!planned, "Distage provider registration is already frozen")
    val all = registrations ++ tests
    require(all.map(_.descriptor.id).distinct.size == all.size, "Duplicate distage provider identities")
    registrations = all
  }

  override def resolve(tests: Vector[TestDescriptor], overrides: RunOverrides): Either[Failure, Vector[TestDescriptor]] = synchronized {
    require(!planned, "Distage provider planning has already started")
    tests.foldLeft[Either[Failure, Vector[ResolvedDistageTest]]](Right(Vector.empty)) { (previous, descriptor) =>
      previous.flatMap { values =>
        registrations.find(_.descriptor.id == descriptor.id) match {
          case None => Left(RunnerFailure.message(FailurePhase.Selection, s"Unknown distage provider identity: ${descriptor.id}"))
          case Some(registration) => registration.resolve(overrides).flatMap { resolved =>
            if (resolved.descriptor.id != descriptor.id) Left(RunnerFailure.message(FailurePhase.Selection, "Distage resolution changed a test identity"))
            else Right(values :+ resolved)
          }
        }
      }
    }.map { values =>
      val selected = values.filter(value => overrides.axisFilters.forall(value.descriptor.settings.axes.contains))
      selected.foreach(value => resolutions += value.descriptor -> value.test)
      selected.map(_.descriptor)
    }
  }

  override def plan(tests: Vector[TestDescriptor]): Future[ExecutionPlan] = {
    implicit val ec: ExecutionContext = executionContext
    val selected = synchronized {
      require(!planned, "Distage provider planning has already started")
      planned = true
      tests.zipWithIndex.map { case (descriptor, index) =>
        val test = resolutions.getOrElse(descriptor, throw new IllegalStateException(s"Unresolved distage test: ${descriptor.id}"))
        test.copy(testMeta = TestMeta(test.testMeta.id, test.testMeta.pos, index.toLong))
      }
    }
    val reporter = new DistageProviderReporter(tests)
    val runner = engine.runner(reporter)
    effectRunner.runFuture(runner.plan(selected)).map { prepared =>
      new ExecutionPlan {
        override val tests: Vector[TestDescriptor] = reporter.tests
        private val started = new AtomicBoolean(false)

        override def execute(context: RunExecutionContext): Future[ProviderOutcome] = {
          require(started.compareAndSet(false, true), "Distage execution plan has already started")
          reporter.begin(context)
          if (context.cancellation.isRequested) Future.successful(reporter.cancelled())
          else {
            effectRunner.runFuture(runner.runPrepared(prepared)).map(_ => reporter.outcome(Vector.empty, context.cancellation.isRequested)).recover {
              case NonFatal(cause) => reporter.executionFailed(cause, context.cancellation.isRequested)
            }
          }
        }
      }
    }
  }
}
