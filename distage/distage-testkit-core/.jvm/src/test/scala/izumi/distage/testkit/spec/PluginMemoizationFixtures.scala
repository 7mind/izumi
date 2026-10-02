package izumi.distage.testkit.spec

import distage.{DIKey, Functoid, TagK}
import com.typesafe.config.ConfigFactory
import izumi.distage.config.model.AppConfig
import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.PluginConfig
import izumi.distage.plugins.load.PluginLoaderDefaultImpl
import izumi.distage.plugins.merge.SimplePluginMergeStrategy
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.testkit.model.*
import izumi.distage.testkit.runner.TestkitRunnerModule
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.spec.sessionplugins.{SessionMemoizedValue, SessionScannedPlugin}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.SourceFilePosition
import izumi.fundamentals.platform.language.types.HigherKindedAny.AnyF
import izumi.fundamentals.platform.language.Quirks.*

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters.*

private[spec] object PluginMemoizationFixtures {
  def checks(): Vector[(String, Boolean)] = {
    val config = PluginConfig(Seq("izumi.distage.testkit.spec.sessionplugins"), Nil, cachePackages = true, debug = false, Nil, Nil)
    val unrelated = new ModuleDef { make[Int].fromValue(42) }
    val firstExtraPackage = "izumi.distage.testkit.spec.nomatching.first"
    val secondExtraPackage = "izumi.distage.testkit.spec.nomatching.second"
    val multiplePackages = config.enablePackage(firstExtraPackage)
    val excludedPackages = config.copy(packagesDisabled = Seq(firstExtraPackage, secondExtraPackage))
    Vector(
      Request("same", config, config, shared = true, mapped = false),
      Request("merges", config, config.copy(merges = Seq(unrelated)), shared = true, mapped = false),
      Request("overrides", config, config.copy(overrides = Seq(unrelated)), shared = true, mapped = false),
      Request("debug", config, config.copy(debug = true), shared = true, mapped = false),
      Request("subset", multiplePackages, config, shared = true, mapped = false),
      Request("overlap", multiplePackages, config.enablePackage(secondExtraPackage), shared = true, mapped = false),
      Request("reorder", multiplePackages, multiplePackages.copy(packagesEnabled = multiplePackages.packagesEnabled.reverse), shared = true, mapped = false),
      Request("parent-child", config.copy(packagesEnabled = Seq("izumi.distage.testkit.spec")), config, shared = false, mapped = false),
      Request("exclusions-unrelated", config, config.disablePackage(firstExtraPackage), shared = false, mapped = false),
      Request("exclusions-order", excludedPackages, excludedPackages.copy(packagesDisabled = excludedPackages.packagesDisabled.reverse), shared = false, mapped = false),
      Request("mapped", config, config.copy(merges = Seq(unrelated)), shared = true, mapped = true),
    ).map(check)
  }

  private def check(request: Request): (String, Boolean) = {
    val loader = new SessionPluginLoader(
      cache => {
        val delegate = PluginLoaderDefaultImpl.withPackageCache(cache)
        if (request.mapped) delegate.map(loaded => loaded) else delegate
      }
    )
    val configs = Vector(request.first, request.second)
    val plugins = configs.map(loader.load).map {
      loaded =>
        require(loaded.loaded.size == 1, "Memoization must scan exactly the fixture plugin")
        loaded.loaded.head.asInstanceOf[SessionScannedPlugin]
    }
    val defaultModule = DefaultModule.empty[Identity]
    val roles = RolesInfo(Set.empty, Set.empty, Set.empty, Set.empty, Set.empty, Set.empty)
    val factory = new TestEnvironmentFactory.Impl
    val base = TestConfig.empty.copy(
      memoizationRoots = TestConfig.PriorityAxisDIKeys.fromSet(Set(DIKey[SessionMemoizedValue])),
      parallelEnvs = TestConfig.Parallelism.Sequential,
      parallelSuites = TestConfig.Parallelism.Sequential,
      parallelTests = TestConfig.Parallelism.Sequential,
      activationStrategy = TestActivationStrategy.IgnoreConfig,
      configOverrides = Some(AppConfig.provided(ConfigFactory.empty())),
    )
    val environments = configs.map {
      request => factory.create[Identity](base.copy(pluginConfig = request), loader, roles, SimplePluginMergeStrategy, TagK[Identity], () => defaultModule.module)
    }
    require(plugins.forall(p => p.provisions.get() == 0 && p.acquired.get() == 0 && p.released.get() == 0), "Loading environments must not provision resources")
    val resources = new ConcurrentLinkedQueue[SessionMemoizedValue]()
    val tests = environments.zipWithIndex.map {
      case (environment, index) =>
        val suite = SuiteId("PluginMemoizationSuite" + index)
        DistageTest[Identity](
          Functoid { (resource: SessionMemoizedValue) => resources.add(resource).discard() },
          environment,
          TestMeta(TestId(Seq("shares its resource"), suite), SourceFilePosition("PluginMemoizationFixtures.scala", 1), index.toLong),
          SuiteMeta(suite, suite.suiteId, suite.suiteId),
        ).asInstanceOf[DistageTest[AnyF]]
    }
    val reporter = new RecordingReporter
    TestkitRunnerModule.run[Identity](reporter, _ => false, tests, Nil).discard()
    val distinctPlugins = plugins.foldLeft(Vector.empty[SessionScannedPlugin]) {
      (distinct, plugin) => if (distinct.exists(_ eq plugin)) distinct else distinct :+ plugin
    }
    val acquired = distinctPlugins.map(_.acquired.get()).sum
    val released = distinctPlugins.map(_.released.get()).sum
    val values = resources.asScala.toVector
    val shared = values.size == 2 && (values.head eq values.last)
    println("SESSION_PLUGIN_MEMOIZATION variant=" + request.variant + " successes=" + reporter.successes.get() + " failures=" + reporter.failures.get() +
      " shared=" + shared + " acquired=" + acquired + " released=" + released)
    require(reporter.successes.get() == 2 && reporter.failures.get() == 0 && reporter.ended, "The actual engine must complete both bodies successfully")
    val expectedAcquisitions = if (request.shared) 1 else 2
    ("JVM plugin " + request.variant + " preserves memoization boundaries and releases its resources",
      shared == request.shared && acquired == expectedAcquisitions && released == expectedAcquisitions)
  }

  private final case class Request(variant: String, first: PluginConfig, second: PluginConfig, shared: Boolean, mapped: Boolean)

  private final class RecordingReporter extends TestReporter {
    val successes = new AtomicInteger(0)
    val failures = new AtomicInteger(0)
    @volatile var ended = false
    override def beginScope(id: ScopeId): Unit = ()
    override def endScope(id: ScopeId): Unit = { ended = true }
    override def beginLevel(scope: ScopeId, depth: Int, suites: List[SuiteMeta]): Unit = ()
    override def endLevel(scope: ScopeId, depth: Int, suites: List[SuiteMeta]): Unit = ()
    override def beginSuite(scope: ScopeId, depth: Int, suiteMeta: SuiteMeta): Unit = ()
    override def endSuite(scope: ScopeId, depth: Int, suiteMeta: SuiteMeta): Unit = ()
    override def testSetupStatus(scope: ScopeId, depth: Int, meta: FullMeta, status: TestStatus.Setup): Unit = failures.incrementAndGet().discard()
    override def testStatus(scope: ScopeId, depth: Int, meta: FullMeta, status: TestStatus): Unit = status match {
      case _: TestStatus.Succeed => successes.incrementAndGet().discard()
      case _: TestStatus.Done => failures.incrementAndGet().discard()
      case _ => ()
    }
  }
}
