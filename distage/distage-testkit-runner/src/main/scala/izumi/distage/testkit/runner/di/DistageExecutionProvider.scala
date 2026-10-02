package izumi.distage.testkit.runner.di

import izumi.distage.model.definition.{Activation, Axis}
import izumi.distage.testkit.model.{DistageTest, TestActivationStrategy, TestConfig, TestEnvironment, TestMeta}
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*
import izumi.distage.testkit.runner.impl.services.{TestActivationResolver, TestConfigLoader}
import izumi.distage.testkit.spec.{SessionPluginLoader, SessionTestEnvironment, TestEnvironmentFactory}
import izumi.distage.plugins.load.PluginLoaderDefaultImpl
import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.quasi.QuasiIORunner
import izumi.fundamentals.platform.language.types.HigherKindedAny.AnyF
import izumi.logstage.api.IzLogger

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

private[distage] final case class ResolvedDistageTest(descriptor: TestDescriptor, test: DistageTest[AnyF])
private[distage] final case class RegisteredDistageTest(descriptor: TestDescriptor, resolve: RunOverrides => Either[Failure, ResolvedDistageTest])
private[distage] final case class EffectiveDistageEnvironment(environment: TestEnvironment, settings: EffectiveSettings)
private[di] final case class EnvironmentResolution(environment: SessionTestConfigLoader.EnvironmentIdentity, overrides: RunOverrides)

final class DistageExecutionProvider(
  executionContext: ExecutionContext,
  configLoader: TestConfigLoader,
  options: DistageRunnerOptions,
) extends ExecutionProvider {
  private[distage] val defaultPluginLoader = new SessionPluginLoader(cache => PluginLoaderDefaultImpl.withPackageCache(cache))
  private[distage] val environments = new SessionTestEnvironment(new SessionEnvironmentFactory(new TestEnvironmentFactory.Impl, new SessionBootstrapFactory, defaultPluginLoader.packageCache))
  private type RunnerF[A] = MiniBIOAsync[Throwable, A]
  private val configuration = new SessionTestConfigLoader(configLoader)
  private val engine = new DistageEngine[RunnerF](configuration, options)
  private val effectRunner = QuasiIORunner.fromBIO[MiniBIOAsync](using MiniBIOAsync.UnsafeRunMiniBIOAsync(using executionContext))
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
