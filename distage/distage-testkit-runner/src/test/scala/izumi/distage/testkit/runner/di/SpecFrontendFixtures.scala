package izumi.distage.testkit.runner.di

import distage.{DefaultModule2, DIKey, Functoid, ModuleDef, TagK}
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.PluginConfig
import izumi.distage.plugins.load.{LoadedPlugins, PluginLoader, PluginLoaderFactory, PluginPackageCache}
import izumi.distage.plugins.merge.PluginMergeStrategy
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.testkit.model.{TestActivationStrategy, TestConfig}
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*
import izumi.distage.testkit.runner.spec.{PluginLoaderFactoryConfiguration, Spec1, Spec2, SpecIdentity, SpecZIO}
import izumi.distage.testkit.spec.{DistageTestEnv, TestConfiguration}
import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.fundamentals.assertions.bio.BIOAssertionSuspension.*
import izumi.fundamentals.assertions.cats.CatsAssertionSuspension.*
import izumi.logstage.api.routing.StaticLogRouter
import izumi.logstage.distage.LogIO2Module
import zio.ZIO
import cats.effect.IO

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

private[di] object SpecFrontendFixtures {
  def run(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val stats = new Statistics
    val identity = CatalogueIdentity(BuildId("spec-fixtures"), BuildTargetId("spec-target"), CatalogueId("spec-catalogue"))
    val sink = new FixtureSupport.RecordingSink
    def events: Vector[ProtocolMessage.Event] = sink.events
    val session = new RunSession(identity, factories(stats), context, sink)
    val initialRouter = StaticLogRouter.instance.get()
    val catalogue = session.discover().fold(failure => throw new IllegalStateException(failure.message), value => value)
    verify("all four spec entry points discover their structured tests", catalogue.suites.size == 4 && catalogue.tests.size == 18)
    verify("spec discovery suspends configuration, hooks, effect construction and bodies", stats.configurations.get() == 0 && stats.environments.get() == 0 && stats.loaders.get() == 0 && stats.effectsBuilt == 0 && stats.bodies.get() == 0 && stats.assertions.get() == 0 && stats.acquired.get() == 0 && events.isEmpty)
    verify("nested registration restores the outer structured path", catalogue.tests.exists(_.id.path == Vector("outer", "should", "inner", "can", "nested")) && catalogue.tests.exists(_.id.path == Vector("outer", "should", "after inner")) && catalogue.tests.exists(_.id.path == Vector("root")))
    verify("spec registration captures source locations", catalogue.tests.forall(_.location.isInstanceOf[SourceLocation.Known]))
    val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
    val resolved = session.resolve(request).fold(failure => throw new IllegalStateException(failure.message), value => value)
    verify("selected resolution invokes each suite configuration and environment hook once", stats.configurations.get() == 4 && stats.environments.get() == 4 && stats.loaders.get() == 4 && stats.roles.get() == 4 && stats.merges.get() == 4)
    verify("selected resolution preserves roles, merge and loader hook order", stats.hookOrder == Vector.fill(4)(Vector("roles", "merge", "loader")).flatten)
    verify("selected resolution retains logical identities and effective settings", resolved.tests.map(_.id) == catalogue.tests.map(_.id) && resolved.tests.forall(_.settings.memoization))
    verify("selected resolution suspends bodies and application resources", stats.effectsBuilt == 0 && stats.bodies.get() == 0 && stats.assertions.get() == 0 && stats.acquired.get() == 0 && stats.released.get() == 0)
    session.plan(resolved).flatMap { planned =>
      verify("spec planning preserves the global router and leaves application resources untouched", (StaticLogRouter.instance.get() eq initialRouter) && stats.acquired.get() == 0 && stats.bodies.get() == 0 && stats.effectsBuilt == 0)
      session.execute(RunId("spec-frontends"), planned.fold(failure => throw new IllegalStateException(failure.message), value => value)).map { outcome =>
        println("DISTAGE_SPEC_FRONTENDS results=" + outcome.results.size + " acquired=" + stats.acquired.get() + " released=" + stats.released.get() + " built=" + stats.effectsBuilt + " bodies=" + stats.bodies.get())
        verify("spec execution reports every selected identity exactly once", outcome.results.map(_.id).toSet == catalogue.tests.map(_.id).toSet && outcome.results.size == 18)
        verify("plain, unary, bifunctor and environment bodies execute under DI", stats.bodies.get() == 12 && outcome.results.count(_.status == TestStatus.Succeeded) == 12)
        verify("effect construction is deferred to selected execution", stats.built.get() == 2 && stats.catsBuilt.get() == 3 && stats.zioBuilt.get() == 4)
        verify("typed bifunctor errors remain test failures", outcome.results.count(result => result.status == TestStatus.Failed && result.failure.exists(failure => failure.phase == FailurePhase.Test && failure.exceptionClass.contains("TypedError"))) == 1)
        def diagnostics(failure: Failure): Vector[AssertionDiagnostic] = failure.assertion.toVector ++ failure.causes.flatMap(diagnostics)
        val assertions = outcome.results.filter(_.id.path.last == "assertion")
        verify("Identity, Cats, BIO and environment assertions retain structured failures", assertions.size == 4 && stats.assertions.get() == 4 && assertions.forall(result => result.status == TestStatus.Failed && result.failure.toVector.flatMap(diagnostics).size == 1))
        verify("all assertion effects retain observations and source metadata", assertions.flatMap(_.failure).flatMap(diagnostics).forall(diagnostic => diagnostic.source.identity.path.endsWith("SpecFrontendFixtures.scala") && diagnostic.source.span != DiagnosticSpan.Unavailable && diagnostic.sourceValidation == DiagnosticSourceValidation.Unavailable && diagnostic.observations.exists(_.value == ObservedValue.Evaluated("false")) && diagnostic.omittedObservations == 0))
        verify("all spec assertion results and events preserve their wire diagnostics", ProtocolCodec.decode(ProtocolCodec.encode(ProtocolMessage.Completed(outcome))) == Right(ProtocolMessage.Completed(outcome)) && events.forall(event => ProtocolCodec.decode(ProtocolCodec.encode(event)) == Right(event)))
        println("DISTAGE_SPEC_ASSERTION_TRANSPORT effects=Identity,Cats,BIO,environment assertions=4 wire=verified")
        verify("skip retains its test identity without evaluating its argument", outcome.results.count(_.status == TestStatus.Cancelled) == 1 && stats.skipped.get() == 0)
        verify("compatible spec environments share and release one resource per effect", stats.acquired.get() == 3 && stats.released.get() == 3 && outcome.failures.isEmpty)
        verify("spec completion follows resource release", events.last.event == RunEvent.Finished(outcome.run, outcome) && !outcome.successful)
      }
    }.flatMap(_ => unselected(context, verify)).flatMap(_ => planningHooks(context, verify))
  }

  private def factories(stats: Statistics): Vector[() => TestSuite] = Vector[() => TestSuite](
    () => new IdentitySuite(stats),
    () => new UnarySuite(stats),
    () => new BifunctorSuite(stats)(using stats.zioDefaults),
    () => new EnvironmentSuite(stats)(using stats.zioDefaults, distage.TagK3[ZIO], distage.TagKK[zio.IO]),
  )

  private def unselected(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val stats = new Statistics
    val identity = CatalogueIdentity(BuildId("unselected-specs"), BuildTargetId("spec-target"), CatalogueId("unselected-specs"))
    val sink = FixtureSupport.silentSink()
    val session = new RunSession(identity, factories(stats), context, sink)
    val catalogue = session.discover().fold(failure => throw new IllegalStateException(failure.message), value => value)
    val selected = catalogue.tests.find(_.id.path == Vector("identity", "should", "dependency")).getOrElse(throw new IllegalStateException("Missing unselected-specs control test"))
    val request = RunRequest(identity, Selection.Only(Vector.empty, Vector(selected.id)), RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
    session.execute(RunId("unselected-specs"), request).map { outcome =>
      verify("unselected effect suites retain suspended configuration and environment hooks", stats.configurations.get() == 1 && stats.environments.get() == 1 && stats.loaders.get() == 1)
      verify("unselected unary and ZIO effect expressions are never constructed", stats.effectsBuilt == 0 && stats.bodies.get() == 1 && stats.skipped.get() == 0)
      verify("explicit test selection provisions and releases only its selected graph", outcome.successful && outcome.results.map(_.id) == Vector(selected.id) && stats.acquired.get() == 1 && stats.released.get() == 1)
    }
  }

  private def planningHooks(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    Vector("environment", "loader").foldLeft(Future.successful(())) { (before, kind) => before.flatMap { _ =>
      val stats = new Statistics
      val hooks = new AtomicInteger(0)
      val original = new IllegalStateException("controlled " + kind + " planning hook failure")
      val identity = CatalogueIdentity(BuildId("planning-hooks"), BuildTargetId("spec-target"), CatalogueId(kind))
      val suite = new SpecIdentity {
        override protected def config: TestConfig = stats.configuration(TestConfig.empty)
        override protected def makeTestEnv(): izumi.distage.testkit.model.TestEnvironment = {
          if (kind == "environment") { hooks.incrementAndGet().discard(); throw original }
          super.makeTestEnv()
        }
        override protected def makePluginLoaderFactory(): PluginLoaderFactory = {
          if (kind == "loader") new PluginLoaderFactory {
            override def create(packageCache: PluginPackageCache): PluginLoader = {
              val _ = packageCache
              new PluginLoader {
                override def load(config: PluginConfig): LoadedPlugins = { hooks.incrementAndGet().discard(); throw original }
              }
            }
          } else super.makePluginLoaderFactory()
        }
        "body" in { (resource: Resource) => stats.body(resource) }
      }
      val sink = FixtureSupport.silentSink()
      val session = new RunSession(identity, Vector(() => suite), context, sink)
      val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
      verify(kind + " planning hook is suspended during discovery", session.discover().isRight && hooks.get() == 0 && stats.acquired.get() == 0 && stats.bodies.get() == 0)
      val resolved = session.resolve(request)
      session.execute(RunId(kind), request).map { outcome =>
        verify(kind + " planning hook retains its phase and original exception", resolved.left.toOption.exists(_.phase == FailurePhase.Planning) && outcome.failures.size == 1 && outcome.failures.head.phase == FailurePhase.Planning && outcome.failures.head.exceptionClass == original.getClass.getName && outcome.failures.head.message == original.getMessage)
        verify(kind + " planning failure retains one failed snapshot without provisioning", hooks.get() == 1 && stats.acquired.get() == 0 && stats.released.get() == 0 && stats.bodies.get() == 0 && outcome.results.isEmpty)
      }
    } }
  }

  private final class Resource

  private final class Statistics {
    val configurations = new AtomicInteger(0)
    val environments = new AtomicInteger(0)
    val loaders = new AtomicInteger(0)
    val roles = new AtomicInteger(0)
    val merges = new AtomicInteger(0)
    val built = new AtomicInteger(0)
    val catsBuilt = new AtomicInteger(0)
    val zioBuilt = new AtomicInteger(0)
    def effectsBuilt: Int = built.get() + catsBuilt.get() + zioBuilt.get()
    val bodies = new AtomicInteger(0)
    val assertions = new AtomicInteger(0)
    val skipped = new AtomicInteger(0)
    val acquired = new AtomicInteger(0)
    val released = new AtomicInteger(0)
    var hookOrder = Vector.empty[String]
    val zioDefaults: DefaultModule[zio.Task] = implicitly[DefaultModule[zio.Task]]
    val zioLogging: distage.Module = LogIO2Module[zio.IO]()
    private val definitions = new ModuleDef {
      make[Resource].fromResource(() => Lifecycle.make[Identity, Resource] {
        acquired.incrementAndGet().discard()
        new Resource
      } { _ => released.incrementAndGet().discard() })
    }
    def configuration(base: TestConfig): TestConfig = {
      configurations.incrementAndGet().discard()
      base.copy(pluginConfig = PluginConfig.constUnchecked(definitions), memoizationRoots = TestConfig.PriorityAxisDIKeys.fromSet(Set(DIKey[Resource])), activationStrategy = TestActivationStrategy.IgnoreConfig)
    }
    def body(resource: Resource): Unit = { require(resource != null); bodies.incrementAndGet().discard() }
  }

  private trait Configured extends TestConfiguration with DistageTestEnv with PluginLoaderFactoryConfiguration {
    protected def stats: Statistics
    abstract override protected def config: TestConfig = stats.configuration(super.config)
    abstract override protected def makePluginLoaderFactory(): PluginLoaderFactory = {
      require(stats.hookOrder.lastOption.contains("merge"), "Plugin loader requires previously initialized roles and merge strategy")
      stats.hookOrder :+= "loader"
      stats.loaders.incrementAndGet().discard()
      val underlying = super.makePluginLoaderFactory()
      new PluginLoaderFactory {
        override def create(packageCache: PluginPackageCache): PluginLoader = {
          val owned = underlying.create(packageCache)
          new PluginLoader { override def load(config: PluginConfig): LoadedPlugins = owned.load(config) }
        }
      }
    }
    abstract override protected def loadRoles(): RolesInfo = { stats.hookOrder :+= "roles"; stats.roles.incrementAndGet().discard(); super.loadRoles() }
    abstract override protected def makeMergeStrategy(): PluginMergeStrategy = { require(stats.hookOrder.lastOption.contains("roles")); stats.hookOrder :+= "merge"; stats.merges.incrementAndGet().discard(); super.makeMergeStrategy() }
    abstract override private[distage] def loadEnvironment[F[_]](config: TestConfig, tagK: TagK[F], defaultModule: DefaultModule[F]): izumi.distage.testkit.model.TestEnvironment = {
      stats.environments.incrementAndGet().discard()
      super.loadEnvironment[F](config, tagK, defaultModule)
    }
  }

  private final class IdentitySuite(override protected val stats: Statistics) extends SpecIdentity with Configured {
    "identity" should {
      "unit" in { stats.bodies.incrementAndGet().discard() }
      "dependency" in { (resource: Resource) => stats.body(resource) }
      "functoid" in Functoid { (resource: Resource) => stats.body(resource) }
      "skip" skip { stats.skipped.incrementAndGet().discard() }
      "assertion" in { assert(stats.assertions.incrementAndGet() < 0) }
    }
  }

  private final class UnarySuite(override protected val stats: Statistics) extends Spec1[IO] with Configured {
    "outer" should {
      "inner" can { "nested" in { (resource: Resource) => stats.catsBuilt.incrementAndGet().discard(); IO(stats.body(resource)) } }
      "after inner" in { (resource: Resource) => stats.catsBuilt.incrementAndGet().discard(); IO(stats.body(resource)) }
    }
    "root" in { stats.catsBuilt.incrementAndGet().discard(); IO(stats.bodies.incrementAndGet().discard()) }
    "assertion" in { assert1[IO](stats.assertions.incrementAndGet() < 0) }
  }

  private final class BifunctorSuite(override protected val stats: Statistics)(implicit defaults: DefaultModule2[zio.IO]) extends Spec2[zio.IO] with Configured {
    override protected def config: TestConfig = super.config.copy(moduleOverrides = stats.zioLogging)
    "bifunctor" must {
      "effect" in { stats.built.incrementAndGet().discard(); ZIO.succeed(stats.bodies.incrementAndGet().discard()) }
      "dependency" in { (resource: Resource) => ZIO.succeed(stats.body(resource)) }
      "typed error" in { stats.built.incrementAndGet().discard(); ZIO.fail("controlled typed test failure") }
      "assertion" in { assert2[zio.IO](stats.assertions.incrementAndGet() < 0) }
    }
  }

  private final class EnvironmentSuite(override protected val stats: Statistics)(implicit defaults: distage.DefaultModule3[ZIO], tag3: distage.TagK3[ZIO], tag2: distage.TagKK[zio.IO]) extends SpecZIO with Configured {
    override protected def config: TestConfig = super.config.copy(moduleOverrides = stats.zioLogging)
    "environment" should {
      "environment" in { stats.zioBuilt.incrementAndGet().discard(); ZIO.serviceWith[Resource](stats.body) }
      "parameter and environment" in { (resource: Resource) => stats.zioBuilt.incrementAndGet().discard(); ZIO.serviceWith[Resource] { environment => require(resource eq environment); stats.body(resource) } }
      "empty environment" in { stats.zioBuilt.incrementAndGet().discard(); ZIO.succeed(stats.bodies.incrementAndGet().discard()) }
      "functoid environment" in Functoid { (_: Resource) => stats.zioBuilt.incrementAndGet().discard(); ZIO.serviceWith[Resource](stats.body) }
      "assertion" in { assert2[zio.IO](stats.assertions.incrementAndGet() < 0) }
    }
  }
}
