package izumi.distage.testkit.runner.di

import izumi.distage.model.definition.{Activation, Axis}
import izumi.distage.testkit.model.{DistageTest, TestActivationStrategy, TestConfig, TestEnvironment, TestMeta}
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*
import izumi.distage.testkit.runner.api.TestFinalizationReporter
import izumi.distage.testkit.runner.impl.services.{TestActivationResolver, TestConfigLoader}
import izumi.distage.testkit.spec.{SessionTestEnvironment, TestEnvironmentFactory}
import izumi.distage.plugins.load.{PluginLoader, PluginLoaderFactory}
import izumi.fundamentals.platform.language.types.HigherKindedAny.AnyF
import izumi.logstage.api.IzLogger

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure as FutureFailure, Success}
import scala.util.control.NonFatal

private[distage] final case class ResolvedDistageTest(descriptor: TestDescriptor, test: DistageTest[AnyF])
private[distage] final case class RegisteredDistageTest(descriptor: TestDescriptor, resolve: RunOverrides => Either[Failure, ResolvedDistageTest])
private[distage] final case class EffectiveDistageEnvironment(environment: TestEnvironment, settings: EffectiveSettings)
private[di] final case class EnvironmentResolution(environment: SessionTestConfigLoader.EnvironmentIdentity, overrides: RunOverrides)

final class DistageExecutionProvider(
  executionContext: ExecutionContext,
  configLoader: TestConfigLoader,
  options: DistageRunnerOptions,
  runtime: TestRunnerRuntime,
) extends ExecutionProvider {
  def this(executionContext: ExecutionContext, configLoader: TestConfigLoader, options: DistageRunnerOptions) =
    this(executionContext, configLoader, options, TestRunnerRuntime.defaultPlatformRuntime)

  private val pluginLoaders = new SessionPluginLoaders
  private[distage] def defaultPluginLoaderFactory: PluginLoaderFactory = pluginLoaders.defaultFactory
  private[distage] def pluginLoader(factory: PluginLoaderFactory): PluginLoader = pluginLoaders.load(factory)
  private[distage] val environments = new SessionTestEnvironment(new SessionEnvironmentFactory(new TestEnvironmentFactory.Impl, new SessionBootstrapFactory))
  private val configuration = new SessionTestConfigLoader(configLoader)
  private var registeredRuntimes = Map.empty[TestId, TestRunnerRuntime]
  private var registrations = Vector.empty[RegisteredDistageTest]
  private var resolutions = Map.empty[TestDescriptor, DistageTest[AnyF]]
  private var planned = false
  private val activationResolver = new TestActivationResolver
  private var effectiveEnvironments = Map.empty[EnvironmentResolution, Either[Failure, EffectiveDistageEnvironment]]

  private[distage] def resolveEnvironment(environment: TestEnvironment, overrides: RunOverrides): Either[Failure, EffectiveDistageEnvironment] = synchronized {
    require(!planned, "Distage provider planning has already started")
    val key = EnvironmentResolution(new SessionTestConfigLoader.EnvironmentIdentity(environment), overrides)
    effectiveEnvironments.getOrElse(key, {
      def choice(request: AxisChoice): Either[Failure, (Axis, Axis.AxisChoice)] = {
        environment.activationInfo.availableChoices.find(_._1.name == request.axis.value).flatMap { case (axis, choices) =>
          choices.find(_.value == request.value.value).map(axis -> _)
        }.toRight(RunnerFailure.message(FailurePhase.Selection, s"Unknown activation choice: ${request.axis.value}:${request.value.value}"))
      }
      val validated = (overrides.axes ++ overrides.axisFilters).foldLeft[Either[Failure, Vector[(Axis, Axis.AxisChoice)]]](Right(Vector.empty)) { (previous, request) =>
        previous.flatMap(values => choice(request).map(values :+ _))
      }
      val result = validated.flatMap { choices =>
        try {
          val logger = IzLogger(environment.logLevel).withCustomContext("phase" -> "testRunner")
          val config = configuration.loadConfig(environment, logger)
          val activation = activationResolver.resolve(config, environment, logger) ++ Activation(choices.take(overrides.axes.size).toMap)
          val enabled = overrides.memoization != MemoizationOverride.Disabled
          val effective = environment.copy(
            activation = activation,
            activationStrategy = TestActivationStrategy.IgnoreConfig,
            configOverrides = Some(config),
            memoizationRoots = if (enabled) environment.memoizationRoots else TestConfig.PriorityAxisDIKeys.empty,
          )(environment.parallelSuites, environment.parallelTests, environment.debugOutput)
          configuration.retain(effective, config)
          val axes = activation.activeChoices.toVector.map { case (axis, value) => AxisChoice(AxisId(axis.name), AxisValue(value.value)) }.sortBy(_.axis.value)
          Right(EffectiveDistageEnvironment(effective, EffectiveSettings(axes, enabled)))
        } catch { case NonFatal(cause) => Left(RunnerFailure.fromThrowable(FailurePhase.Planning, cause)) }
      }
      effectiveEnvironments += key -> result
      result
    })
  }

  private[distage] def add(tests: Vector[RegisteredDistageTest], factory: TestRunnerRuntime): Unit = synchronized {
    add(tests)
    registeredRuntimes ++= tests.map(test => test.descriptor.id -> factory)
  }

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

  override def plan(tests: Vector[TestDescriptor]): Future[ExecutionPlan] = plan(tests, new Cancellation)

  override def plan(tests: Vector[TestDescriptor], cancellation: Cancellation): Future[ExecutionPlan] = {
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
    final class Finalization extends TestFinalizationReporter {
      private var observed = Vector.empty[Throwable]
      def failures: Vector[Failure] = synchronized(observed.map(RunnerFailure.fromThrowable(FailurePhase.Finalization, _)))
      override def failure(cause: Throwable): Unit = synchronized { observed :+= cause }
    }
    val finalization = new Finalization
    tests.headOption.flatMap(test => registeredRuntimes.get(test.id)).getOrElse(runtime).prepare(reporter, reporter, finalization, _.isInstanceOf[TestCancelled], selected, configuration, options, cancellation).flatMap { prepared =>
      try Future.successful(new ExecutionPlan {
        override val tests: Vector[TestDescriptor] = reporter.tests
        override val inspection: PlanInspection = DistagePlanInspection(prepared.planned.out, tests)
        private var execution = Option.empty[Promise[ProviderOutcome]]
        private var closing = Option.empty[Promise[Unit]]

        override def close(): Future[Unit] = {
          val (completion, admitted) = synchronized {
            closing match {
              case Some(previous) => (previous.future, None)
              case None =>
                val requested = Promise[Unit]()
                closing = Some(requested)
                (requested.future, Some((requested, execution)))
            }
          }
          admitted.foreach { case (requested, active) =>
            val released = active match {
              case Some(executing) => executing.future.transform(_ => Success(()))
              case None => prepared.close()
            }
            val _ = requested.completeWith(released)
          }
          completion
        }

        override def execute(context: RunExecutionContext): Future[ProviderOutcome] = {
          val completion = synchronized {
            require(closing.isEmpty, "Distage execution plan is closed")
            require(execution.isEmpty, "Distage execution plan has already started")
            val admitted = Promise[ProviderOutcome]()
            execution = Some(admitted)
            admitted
          }
          reporter.begin(context)
          def failures(cause: Throwable): Vector[Failure] = Vector(RunnerFailure.fromThrowable(FailurePhase.Finalization, cause))
          val result = if (context.cancellation.isRequested) prepared.close().transform { released =>
            val retained = released.failed.toOption.toVector.flatMap(failures)
            Success(reporter.cancelRemaining(retained ++ RunnerFailure.unreported(retained, finalization.failures)))
          } else {
            val registration = context.cancellation.onRequest(() => prepared.stop())
            val operation = prepared.execute()
            operation.completion.transformWith { result =>
              registration.close().transform { stopped =>
                val executionFailures = result match {
                  case Success(_) => Vector.empty
                  case FutureFailure(cause) => prepared.failures(cause, context.cancellation.isRequested).flatMap(failures)
                }
                val interruptionFailures = stopped.failed.toOption.toVector.flatMap(failures)
                val retained = executionFailures ++ RunnerFailure.unreported(executionFailures, interruptionFailures ++ finalization.failures)
                Success(
                  if (context.cancellation.isRequested) reporter.cancelRemaining(retained)
                  else if (executionFailures.nonEmpty) reporter.abortRemaining(retained)
                  else reporter.outcome(retained, cancelled = false)
                )
              }
            }
          }
          val _ = completion.completeWith(result)
          completion.future
        }
      }) catch {
        case NonFatal(cause) => prepared.close().transformWith {
          case Success(_) => Future.failed(cause)
          case FutureFailure(release) => Future.failed(new TestRuntime.RuntimeCompletionException(cause, List(release)))
        }
      }
    }
  }
}
