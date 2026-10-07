package izumi.distage.testkit.runner.di

import distage.*
import izumi.distage.config.model.AppConfig
import izumi.distage.plugins.PluginConfig
import izumi.distage.plugins.merge.SimplePluginMergeStrategy
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.testkit.model.{DistageTest, FullMeta, IndividualTestResult, SuiteId as EngineSuiteId, SuiteMeta, TestConfig, TestId as EngineTestId, TestMeta, TestStatus as EngineStatus}
import izumi.distage.testkit.model.TestActivationStrategy
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*
import izumi.distage.testkit.runner.impl.services.{TestConfigLoader, Timing}
import izumi.functional.bio.Exit
import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.{QuasiAsync, QuasiIO, QuasiIORunner}
import izumi.fundamentals.collections.nonempty.NEList
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.language.SourceFilePosition
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.fundamentals.platform.language.types.HigherKindedAny.AnyF
import izumi.fundamentals.platform.uuid.IzUUID
import izumi.logstage.api.IzLogger
import izumi.logstage.api.routing.StaticLogRouter

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import java.time.{OffsetDateTime, ZoneOffset}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.Duration
import scala.util.Try
import scala.util.control.NonFatal

object DistageProviderFixtures {
  private type TestF[A] = MiniBIOAsync[Throwable, A]
  private final class Resource
  private final case class Pair(first: Resource, second: Resource)
  private final class Missing
  private final case class Request(name: String, failBody: Boolean, failRelease: Boolean, failConfig: Boolean, missing: Boolean, holdRelease: Boolean, memoization: MemoizationOverride)

  def main(args: Array[String]): Unit = {
    val _ = args
    ProviderFixturePlatform.run { context =>
      implicit val ec: ExecutionContext = context
      val checks = new Checks
      val requests = Vector(
        Request("success", false, false, false, false, false, MemoizationOverride.Inherit),
        Request("body-failure", true, false, false, false, false, MemoizationOverride.Inherit),
        Request("release-failure", false, true, false, false, false, MemoizationOverride.Inherit),
        Request("configuration-failure", false, false, true, false, false, MemoizationOverride.Inherit),
        Request("provisioning-failure", false, false, false, true, false, MemoizationOverride.Inherit),
        Request("memoization-disabled", false, false, false, false, false, MemoizationOverride.Disabled),
      )
      requests.foldLeft(Future.successful(())) { (before, request) => before.flatMap(_ => exercise(request, checks, context)) }
        .flatMap(_ => abandoned(checks, context))
        .flatMap(_ => cancellation(checks, context))
        .flatMap(_ => heldRelease(checks, context))
        .flatMap(_ => concurrentOwners(checks, context))
        .flatMap(_ => preCancelledTransport(checks, context))
        .flatMap(_ => transport(checks, context, failRelease = false, sameCause = false))
        .flatMap(_ => transport(checks, context, failRelease = true, sameCause = false))
        .flatMap(_ => transport(checks, context, failRelease = true, sameCause = true))
        .flatMap(_ => skipped(checks))
        .flatMap(_ => reporterInvariants(checks))
        .flatMap(_ => abortedReporting(checks))
        .flatMap(_ => SpecFrontendFixtures.run(context, checks.verify))
        .flatMap(_ => SpecAssertionFixtures.run(context, checks.verify))
        .flatMap(_ => SpecInterruptionFixtures.run(context, checks.verify))
        .flatMap(_ => SpecCancellationFixtures.run(context, checks.verify))
        .flatMap(_ => SpecCancellationFixtures.parallel(context, checks.verify))
        .flatMap(_ => SpecCancellationFixtures.applicationChannelLoss(context, checks.verify))
        .flatMap(_ => ResourceFinalizationFixtures.run(context, checks.verify))
        .flatMap(_ => SpecActivationFixtures.run(context, checks.verify))
        .flatMap(_ => ApplicationPlanningFailureFixtures.run(context, checks.verify))
        .flatMap(_ => SpecPlanFixtures.run(context, checks.verify))
        .flatMap(_ => SpecConfigurationFixtures.run(context, checks.verify))
        .flatMap(_ => SpecCompatibilityFixtures.run(context, checks.verify))
        .flatMap(_ => SpecRegistrationFixtures.run(context, checks.verify))
        .flatMap(_ => SpecBootstrapFixtures.run(context, checks.verify))
        .flatMap(_ => SpecPluginRequestFixtures.run(context, checks.verify))
        .flatMap(_ => PluginLoaderFactoryFixtures.run(context, checks.verify))
        .flatMap(_ => ProviderFixturePlatform.pluginOwnership(context, checks.verify))
        .map { _ => println("DISTAGE_PROVIDER_CONTRACTS_OK checks=" + checks.count) }
    }
  }

  private def exercise(request: Request, checks: Checks, context: ExecutionContext): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val fixture = create(request, context)
    val catalogue = fixture.session.discover().fold(failure => throw new IllegalStateException(failure.message), identity)
    checks.verify(request.name + " discovery describes both structured identities", catalogue.tests.map(_.id).toSet == fixture.ids.toSet)
    checks.verify(request.name + " discovery leaves environment, configuration, resources and bodies suspended", fixture.preparations.get() == 0 && fixture.configs.get() == 0 && fixture.acquired.get() == 0 && fixture.bodies.get() == 0 && fixture.sink.events.isEmpty)
    fixture.session.execute(RunId(request.name), fixture.request).map { outcome =>
      val expectedResources = if (request.failConfig || request.missing) 0 else if (request.memoization == MemoizationOverride.Disabled) 2 else 1
      val expectedBodies = if (request.failConfig || request.missing) 0 else 2
      checks.verify(request.name + " evaluates configuration once", fixture.configs.get() == 1)
      checks.verify(request.name + " acquires and releases its intended resource scope", fixture.acquired.get() == expectedResources && fixture.released.get() == expectedResources)
      checks.verify(request.name + " executes only selected bodies", fixture.bodies.get() == expectedBodies)
      checks.verify(request.name + " retains sharing inside each individual graph", fixture.shared.get() == expectedBodies)
      checks.verify(request.name + " emits one terminal run after release", fixture.sink.finished.size == 1 && fixture.sink.finished.head == outcome)
      if (request.failConfig) {
        checks.verify("configuration failure is a planning failure", outcome.results.isEmpty && outcome.failures.size == 1 && outcome.failures.head.phase == FailurePhase.Planning && outcome.failures.head.message == fixture.configFailure.getMessage)
      } else {
        checks.verify(request.name + " execution and reporting identities agree", outcome.results.map(_.id).toSet == fixture.ids.toSet && fixture.sink.completions.map(_.id).toSet == fixture.ids.toSet)
        if (request.failRelease) checks.verify("finalizer failure survives successful body reports", outcome.results.forall(_.status == TestStatus.Succeeded) && outcome.failures.exists(failure => failure.phase == FailurePhase.Finalization && failure.message == fixture.releaseFailure.getMessage) && !outcome.successful)
        else if (request.failBody) checks.verify("body failure retains its test phase", outcome.results.count(_.status == TestStatus.Succeeded) == 1 && outcome.results.count(result => result.failure.exists(_.phase == FailurePhase.Test)) == 1 && !outcome.successful)
        else if (request.missing) checks.verify("provisioning failures remain setup failures", outcome.results.forall(result => result.status == TestStatus.Failed && result.failure.exists(_.phase == FailurePhase.Setup)) && !outcome.successful)
        else checks.verify(request.name + " completes successfully", outcome.successful)
      }
      println("DISTAGE_PROVIDER_CASE name=" + request.name + " configs=" + fixture.configs.get() + " acquired=" + fixture.acquired.get() + " released=" + fixture.released.get() + " bodies=" + fixture.bodies.get() + " results=" + outcome.results.size + " failures=" + outcome.failures.size + " successful=" + outcome.successful)
    }
  }

  private def abandoned(checks: Checks, context: ExecutionContext): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val fixture = create(Request("abandoned", false, false, false, false, false, MemoizationOverride.Inherit), context)
    val initialRouter = StaticLogRouter.instance.get()
    val resolved = fixture.session.resolve(fixture.request).fold(failure => throw new IllegalStateException(failure.message), identity)
    fixture.session.plan(resolved).map { value =>
      checks.verify("abandoned plan contains the complete selected identities", value.exists(_.tests.map(_.id).toSet == fixture.ids.toSet))
      checks.verify("abandoned plan retains one configuration without application acquisition or reports", fixture.configs.get() == 1 && fixture.acquired.get() == 0 && fixture.released.get() == 0 && fixture.bodies.get() == 0 && fixture.sink.events.isEmpty)
      checks.verify("session planning preserves the process-wide logging router", StaticLogRouter.instance.get() eq initialRouter)
    }
  }

  private def cancellation(checks: Checks, context: ExecutionContext): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val fixture = create(Request("cancelled", false, false, false, false, false, MemoizationOverride.Inherit), context)
    fixture.session.cancel()
    fixture.session.execute(RunId("cancelled"), fixture.request).map { outcome =>
      checks.verify("pre-execution cancellation preserves selected terminal identities", outcome.cancelled && outcome.results.map(_.id).toSet == fixture.ids.toSet && outcome.results.forall(_.status == TestStatus.Cancelled))
      checks.verify("pre-execution cancellation acquires no application resource", fixture.acquired.get() == 0 && fixture.released.get() == 0 && fixture.bodies.get() == 0)
    }
  }

  private def heldRelease(checks: Checks, context: ExecutionContext): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val fixture = create(Request("held-release", false, false, false, false, true, MemoizationOverride.Inherit), context)
    val execution = fixture.session.execute(RunId("held-release"), fixture.request)
    fixture.releaseEntered.future.flatMap { _ =>
      checks.verify("body reports precede the controlled finalizer boundary", fixture.sink.completions.size == 2 && fixture.acquired.get() == 1 && fixture.released.get() == 0)
      checks.verify("run completion waits for its finalizer", !execution.isCompleted && fixture.sink.finished.isEmpty)
      fixture.releaseGate.success(())
      execution.map(outcome => checks.verify("releasing the gate completes exactly once", outcome.successful && fixture.released.get() == 1 && fixture.sink.finished == Vector(outcome)))
    }
  }

  private def concurrentOwners(checks: Checks, context: ExecutionContext): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val request = Request("concurrent", false, false, false, false, true, MemoizationOverride.Inherit)
    val first = create(request, context)
    val second = create(request, context)
    val firstRun = first.session.execute(RunId("first-owner"), first.request)
    val secondRun = second.session.execute(RunId("second-owner"), second.request)
    first.releaseEntered.future.flatMap(_ => second.releaseEntered.future).flatMap { _ =>
      checks.verify("concurrent sessions acquire distinct resource objects", first.acquired.get() == 1 && second.acquired.get() == 1 && (first.resource.get() ne second.resource.get()))
      checks.verify("concurrent sessions retain separate configuration and registration", first.configs.get() == 1 && second.configs.get() == 1 && first.sink.completions.size == 2 && second.sink.completions.size == 2)
      first.releaseGate.success(())
      firstRun.flatMap { firstOutcome =>
        checks.verify("completing one owner leaves the other pending", firstOutcome.successful && first.released.get() == 1 && second.released.get() == 0 && !secondRun.isCompleted && second.sink.finished.isEmpty)
        second.releaseGate.success(())
        secondRun.map(secondOutcome => checks.verify("both owners complete after their own release", secondOutcome.successful && second.released.get() == 1 && first.sink.finished == Vector(firstOutcome) && second.sink.finished == Vector(secondOutcome)))
      }
    }
  }

  private def create(request: Request, context: ExecutionContext): Fixture = {
    val fixture = new Fixture(request, context)
    fixture
  }

  private def transport(checks: Checks, context: ExecutionContext, failRelease: Boolean, sameCause: Boolean): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val fixture = create(Request("transport", false, failRelease, false, false, true, MemoizationOverride.Inherit), context)
    val thrown = if (sameCause) fixture.releaseFailure else new IllegalStateException("controlled transport callback failure")
    fixture.sink.failNextStart(thrown)
    val execution = fixture.session.execute(RunId("transport"), fixture.request)
    fixture.releaseEntered.future.flatMap { _ =>
      checks.verify("transport failure preserves selected body execution and reaches cleanup", fixture.acquired.get() == 1 && fixture.bodies.get() == 2 && fixture.released.get() == 0 && fixture.sink.completions.size == 2)
      checks.verify("transport failure completion waits for the finalizer", !execution.isCompleted && fixture.sink.finished.isEmpty)
      fixture.releaseGate.success(())
      execution.map { outcome =>
        checks.verify("transport failure retains its phase and original exception", outcome.failures.exists(failure => failure.phase == FailurePhase.Transport && failure.message == thrown.getMessage && failure.exceptionClass == thrown.getClass.getName))
        checks.verify("reporting and finalization failures remain independent", outcome.failures.exists(_.phase == FailurePhase.Finalization) == failRelease && (!failRelease || outcome.failures.exists(_.message == fixture.releaseFailure.getMessage)))
        checks.verify("transport failure completes after exactly one release attempt", !outcome.successful && fixture.released.get() == 1 && fixture.sink.finished == Vector(outcome))
        checks.verify("partially delivered reporting failure never reuses an event ordinal", fixture.sink.events.map(_.sequence) == fixture.sink.events.indices.map(_.toLong).toVector)
      }
    }
  }

  private def skipped(checks: Checks): Future[Unit] = {
    val suite = SuiteId("SkippedSuite")
    val id = TestId(BuildTargetId("provider-target"), suite, Vector("precondition"), None)
    val descriptor = TestDescriptor(id, "precondition", SourceLocation.Unavailable, EffectiveSettings(Vector.empty, memoization = true))
    val meta = FullMeta(TestMeta(EngineTestId(id.path, EngineSuiteId(suite.value)), SourceFilePosition("DistageProviderFixtures.scala", 1), 0L), SuiteMeta(EngineSuiteId(suite.value), suite.value, suite.value))
    val timing = Timing(OffsetDateTime.now(ZoneOffset.UTC), Duration.Zero)
    val cause = new IllegalStateException("unavailable integration resource")
    val failure = IndividualTestResult.ExecutionFailure(meta, timing, timing, timing, cause, Exit.Trace.forThrowable(cause))
    val reporter = new DistageProviderReporter(Vector(descriptor))
    var events = Vector.empty[ProviderEvent]
    val run = RunId("precondition")
    reporter.begin(RunExecutionContext(run, new Cancellation, event => { events :+= event }))
    reporter.testStatus(izumi.distage.testkit.model.ScopeId(IzUUID.generateTimeUUID()), 0, meta, EngineStatus.IgnoredByPrecondition(failure, NEList(ResourceCheck.ResourceUnavailable("fixture", Some(cause)))))
    val outcome = reporter.outcome(Vector.empty, cancelled = false)
    val result = outcome.results.head
    checks.verify("precondition skip retains its identity without a failure payload", result.id == id && result.status == TestStatus.Skipped && result.failure.isEmpty)
    checks.verify("precondition skip produces a schema-valid terminal event", events == Vector(ProviderEvent.TestCompleted(result)) && ProtocolCodec.validate(ProtocolMessage.Event(0L, RunEvent.TestCompleted(run, result))).isRight)
    Future.successful(())
  }

  private def preCancelledTransport(checks: Checks, context: ExecutionContext): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val fixture = create(Request("cancelled-transport", false, false, false, false, false, MemoizationOverride.Inherit), context)
    val resolved = fixture.session.resolve(fixture.request).fold(failure => throw new IllegalStateException(failure.message), identity)
    fixture.provider.plan(resolved.tests).flatMap { plan =>
      val cancellation = new Cancellation
      cancellation.request()
      val thrown = new IllegalStateException("controlled cancelled-result transport failure")
      val execution = try plan.execute(RunExecutionContext(RunId("cancelled-transport"), cancellation, _ => throw thrown)) catch { case NonFatal(cause) => Future.failed(cause) }
      execution.map { outcome =>
        println("DISTAGE_PROVIDER_CANCELLED_REPORTING results=" + outcome.results.size + " failures=" + outcome.failures.size)
        checks.verify("pre-cancelled reporting failure retains its transport phase", outcome.cancelled && outcome.failures.exists(failure => failure.phase == FailurePhase.Transport && failure.message == thrown.getMessage) && !outcome.failures.exists(_.phase == FailurePhase.Finalization))
        checks.verify("pre-cancelled reporting failure acquires no application resource", fixture.acquired.get() == 0 && fixture.released.get() == 0 && fixture.bodies.get() == 0)
        checks.verify("failed cancellation reporting retains every selected terminal identity", outcome.results.map(_.id) == fixture.ids)
        checks.verify("distinct failed cancellation events retain their failure occurrences", outcome.failures.size == fixture.ids.size)
      }
    }
  }

  private def abortedReporting(checks: Checks): Future[Unit] = {
    val tests = Vector.tabulate(2) { index =>
      val id = TestId(BuildTargetId("provider-target"), SuiteId("AbortedReporterSuite"), Vector(index.toString), None)
      TestDescriptor(id, id.path.mkString, SourceLocation.Unavailable, EffectiveSettings(Vector.empty, memoization = true))
    }
    val reporter = new DistageProviderReporter(tests)
    var events = Vector.empty[ProviderEvent]
    reporter.begin(RunExecutionContext(RunId("aborted-reporter"), new Cancellation, event => { events :+= event }))
    val meta = FullMeta(TestMeta(EngineTestId(tests.head.id.path, EngineSuiteId(tests.head.id.suite.value)), SourceFilePosition("DistageProviderFixtures.scala", 1), 0L), SuiteMeta(EngineSuiteId(tests.head.id.suite.value), tests.head.id.suite.value, tests.head.id.suite.value))
    val timing = Timing(OffsetDateTime.now(ZoneOffset.UTC), Duration.Zero)
    val scope = izumi.distage.testkit.model.ScopeId(IzUUID.generateTimeUUID())
    reporter.testStatus(scope, 0, meta, EngineStatus.Instantiating(izumi.distage.model.plan.Plan.empty, timing, logPlan = false))
    reporter.testStatus(scope, 0, meta, EngineStatus.Succeed(IndividualTestResult.TestSuccess(meta, timing, timing, timing)))
    val completed = reporter.outcome(Vector.empty, cancelled = false).results.head
    val failure = RunnerFailure.fromThrowable(FailurePhase.Finalization, new IllegalStateException("controlled aborted finalizer"))
    checks.verify("aborted reporting rejects termination without a failure", Try(reporter.abortRemaining(Vector.empty)).isFailure)
    val outcome = reporter.abortRemaining(Vector(failure))
    checks.verify("aborted finalization terminalizes every selected test", outcome.results.map(_.id) == tests.map(_.id))
    checks.verify("aborted finalization preserves completed body results", outcome.results.head == completed && completed.status == TestStatus.Succeeded)
    checks.verify("aborted finalization cancels the unexecuted remainder with its cause", outcome.results.last.status == TestStatus.Cancelled && outcome.results.last.failure.contains(failure))
    checks.verify("aborted finalization preserves its failure without external cancellation", outcome.failures == Vector(failure) && !outcome.cancelled)
    checks.verify("aborted finalization emits exactly one completion per selected test", events.collect { case ProviderEvent.TestCompleted(result) => result } == outcome.results)
    checks.verify("aborted finalization starts only the attempted test", events.collect { case ProviderEvent.TestStarted(id) => id } == Vector(tests.head.id))
    Future.successful(())
  }

  private def reporterInvariants(checks: Checks): Future[Unit] = {
    val tests = Vector.tabulate(2) { index =>
      val id = TestId(BuildTargetId("provider-target"), SuiteId("ReporterInvariantSuite"), Vector(index.toString), None)
      TestDescriptor(id, id.path.mkString, SourceLocation.Unavailable, EffectiveSettings(Vector.empty, memoization = true))
    }
    val reporter = new DistageProviderReporter(tests)
    val unbound = Try(reporter.cancelled())
    checks.verify("reporting before begin rejects its context invariant", unbound.failed.toOption.exists(cause => cause.isInstanceOf[IllegalStateException] && cause.getMessage.contains("before provider reporting started")))
    val unchanged = reporter.outcome(Vector.empty, cancelled = false)
    checks.verify("invalid reporting creates no terminal result or transport failure", unchanged.results.isEmpty && unchanged.failures.isEmpty)
    var events = Vector.empty[ProviderEvent]
    val context = RunExecutionContext(RunId("reporter-invariants"), new Cancellation, event => { events :+= event })
    reporter.begin(context)
    val outcome = reporter.cancelled()
    checks.verify("rejected unbound reporting leaves valid reporting available", outcome.results.map(_.id) == tests.map(_.id) && outcome.failures.isEmpty && events.size == tests.size)
    checks.verify("repeated begin rejects an ownership invariant", Try(reporter.begin(context)).failed.toOption.exists(_.isInstanceOf[IllegalArgumentException]))
    checks.verify("repeated completion is an invariant failure outside callback recovery", Try(reporter.cancelled()).isFailure && reporter.outcome(Vector.empty, cancelled = true) == outcome)
    Future.successful(())
  }

  private final class Fixture(options: Request, context: ExecutionContext) {
    private val registeredProvider = new AtomicReference[DistageExecutionProvider]
    def provider: DistageExecutionProvider = Option(registeredProvider.get()).getOrElse(throw new IllegalStateException("Fixture provider has not been registered"))
    val preparations = new AtomicInteger(0)
    val configs = new AtomicInteger(0)
    val acquired = new AtomicInteger(0)
    val released = new AtomicInteger(0)
    val bodies = new AtomicInteger(0)
    val shared = new AtomicInteger(0)
    val resource = new AtomicReference[Resource]
    val releaseEntered = Promise[Unit]()
    val releaseGate = Promise[Unit]()
    val configFailure = new IllegalStateException("fixture configuration failure")
    val releaseFailure = new IllegalStateException("fixture finalizer failure")
    private val F = QuasiIO.fromBIO[MiniBIOAsync]
    private val FA = QuasiAsync.fromBIO[MiniBIOAsync]
    private val innerRunner = QuasiIORunner.fromBIO[MiniBIOAsync](using MiniBIOAsync.UnsafeRunMiniBIOAsync(using context))
    private val definitions = new ModuleDef {
      make[QuasiIO[TestF]].fromValue(F)
      make[QuasiAsync[TestF]].fromValue(FA)
      make[QuasiIORunner[TestF]].fromValue(innerRunner)
      make[Resource].fromResource {
        () => Lifecycle.make[TestF, Resource](F.maybeSuspend {
          val _ = acquired.incrementAndGet()
          val value = new Resource
          resource.set(value)
          value
        }) { _ =>
          val gate = if (options.holdRelease) {
            FA.fromFuture {
              val _ = releaseEntered.trySuccess(())
              releaseGate.future
            }
          } else F.unit
          F.flatMap(gate) { _ => F.maybeSuspend {
            val _ = released.incrementAndGet()
            if (options.failRelease) throw releaseFailure
          } }
        }
      }
      make[Pair].from { (first: Resource, second: Resource) => Pair(first, second) }
    }
    private val defaultModule = DefaultModule.empty[TestF]
    private val config = TestConfig.empty.copy(
      pluginConfig = PluginConfig.constUnchecked(definitions),
      memoizationRoots = TestConfig.PriorityAxisDIKeys.fromSet(Set(DIKey[Resource])),
      activationStrategy = TestActivationStrategy.IgnoreConfig,
      parallelEnvs = TestConfig.Parallelism.Sequential,
      parallelSuites = TestConfig.Parallelism.Sequential,
      parallelTests = TestConfig.Parallelism.Sequential,
    )
    private val identity = CatalogueIdentity(BuildId("provider-fixture"), BuildTargetId("provider-target"), CatalogueId("provider-catalogue"))
    val ids: Vector[TestId] = Vector.tabulate(2)(index => TestId(identity.target, SuiteId("ProviderSuite" + index), Vector("provider", "should", "execute"), None))
    private val loader = new TestConfigLoader {
      override def loadConfig(env: izumi.distage.testkit.model.TestEnvironment, logger: IzLogger): AppConfig = {
        val _ = configs.incrementAndGet()
        if (options.failConfig) throw configFailure
        AppConfig.empty
      }
    }
    val sink = new RecordingSink(acquired, released)
    val request: RunRequest = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, options.memoization))
    val session = new RunSession(identity, ids.zipWithIndex.map { case (id, index) => () => new TestSuite {
      override def register(registration: RegistrationContext): RegisteredSuite = {
        val provider = registration.provider(ProviderId("distage"), () => new DistageExecutionProvider(context, loader, DistageRunnerOptions(false, false)))
        registeredProvider.set(provider)
        val descriptor = TestDescriptor(id, id.path.mkString(" "), SourceLocation.Unavailable, EffectiveSettings(Vector.empty, memoization = true))
        provider.add(Vector(RegisteredDistageTest(descriptor, overrides => {
          if (overrides.axes.nonEmpty || overrides.axisFilters.nonEmpty) Left(RunnerFailure.message(FailurePhase.Selection, "Fixture declares no axes"))
          else {
            val _ = preparations.incrementAndGet()
            val enabled = overrides.memoization != MemoizationOverride.Disabled
            val effectiveConfig = if (enabled) config else config.copy(memoizationRoots = TestConfig.PriorityAxisDIKeys.empty)
            val roles = RolesInfo(Set.empty, Set.empty, Set.empty, Set.empty, Set.empty, Set.empty)
            val environment = provider.environments.load(effectiveConfig, provider.pluginLoader(provider.defaultPluginLoaderFactory), roles, SimplePluginMergeStrategy, TagK[TestF], defaultModule)
            val body = if (options.missing) Functoid { (_: Missing) => F.unit }
            else Functoid { (value: Resource, pair: Pair) => F.maybeSuspend {
              val _ = bodies.incrementAndGet()
              require((value eq pair.first) && (value eq pair.second), "Sharing inside one graph must remain intact")
              shared.incrementAndGet().discard()
              if (options.failBody && index == 0) throw new IllegalStateException("fixture body failure")
            } }
            val suite = EngineSuiteId(id.suite.value)
            val test = DistageTest[TestF](body, environment, TestMeta(EngineTestId(id.path, suite), SourceFilePosition("DistageProviderFixtures.scala", 1), index.toLong), SuiteMeta(suite, id.suite.value, id.suite.value))
            Right(ResolvedDistageTest(descriptor.copy(settings = descriptor.settings.copy(memoization = enabled)), test.asInstanceOf[DistageTest[AnyF]]))
          }
        })))
        RegisteredSuite(SuiteDescriptor(id.suite, id.suite.value), Vector(descriptor), provider)
      }
    } }, context, sink)
  }

  private final class RecordingSink(acquired: AtomicInteger, released: AtomicInteger) extends EventSink {
    private var recorded = Vector.empty[ProtocolMessage.Event]
    private var startFailure = Option.empty[Throwable]
    def failNextStart(cause: Throwable): Unit = synchronized {
      require(recorded.isEmpty && startFailure.isEmpty, "Fixture transport failure must be configured before execution")
      startFailure = Some(cause)
    }
    def events: Vector[ProtocolMessage.Event] = synchronized(recorded)
    def completions: Vector[TestResult] = events.collect { case ProtocolMessage.Event(_, RunEvent.TestCompleted(_, result)) => result }
    def finished: Vector[RunOutcome] = events.collect { case ProtocolMessage.Event(_, RunEvent.Finished(_, outcome)) => outcome }
    override def accept(event: ProtocolMessage.Event): Unit = synchronized {
      event.event match {
        case _: RunEvent.Finished => require(acquired.get() == released.get(), "Session reported completion before resource release")
        case _ => ()
      }
      recorded :+= event
      event.event match {
        case _: RunEvent.TestStarted => startFailure.foreach { cause => startFailure = None; throw cause }
        case _ => ()
      }
    }
  }

  private final class Checks {
    private var checked = 0
    def count: Int = checked
    def verify(name: String, condition: Boolean): Unit = synchronized {
      require(condition, name)
      checked += 1
      println("DISTAGE_PROVIDER_CHECK " + name)
    }
  }
}
