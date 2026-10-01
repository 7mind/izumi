package izumi.distage

import distage.*
import io.circe.parser.parse
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.services.ConfigLoader
import izumi.distage.plugins.{PluginConfig, PluginDef}
import izumi.distage.testkit.model.*
import izumi.distage.testkit.runner.TestkitRunnerModule
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.impl.services.BootstrapFactory
import izumi.distage.testkit.spec.DistageTestEnv
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.SourceFilePosition
import izumi.fundamentals.platform.language.types.HigherKindedAny.AnyF

object PortabilityMain {
  final case class Value(n: Int)
  final case class Shared(acquisition: Int)
  object Counters {
    val acquired = new java.util.concurrent.atomic.AtomicInteger(0)
    val released = new java.util.concurrent.atomic.AtomicInteger(0)
    val threads = java.util.concurrent.ConcurrentHashMap.newKeySet[String]()
  }
  final class RecordingReporter extends TestReporter {
    private val successCount = new java.util.concurrent.atomic.AtomicInteger(0)
    private val failureCount = new java.util.concurrent.atomic.AtomicInteger(0)
    @volatile var ended = false
    def successes: Int = successCount.get()
    def failures: Int = failureCount.get()
    def beginScope(id: ScopeId): Unit = ()
    def endScope(id: ScopeId): Unit = { ended = true }
    def beginLevel(scope: ScopeId, depth: Int, suites: List[SuiteMeta]): Unit = ()
    def endLevel(scope: ScopeId, depth: Int, suites: List[SuiteMeta]): Unit = ()
    def beginSuite(scope: ScopeId, depth: Int, suite: SuiteMeta): Unit = ()
    def endSuite(scope: ScopeId, depth: Int, suite: SuiteMeta): Unit = ()
    def testSetupStatus(scope: ScopeId, depth: Int, meta: FullMeta, status: TestStatus.Setup): Unit = {
      failureCount.incrementAndGet()
      println(s"SETUP_FAILURE $status")
    }
    def testStatus(scope: ScopeId, depth: Int, meta: FullMeta, status: TestStatus): Unit = status match {
      case _: TestStatus.Succeed => successCount.incrementAndGet(); ()
      case _: TestStatus.Done => failureCount.incrementAndGet(); println(s"TEST_FAILURE $status")
      case _ => ()
    }
  }
  final class Environment extends DistageTestEnv {
    def create(config: TestConfig): TestEnvironment = loadEnvironment[Identity](config, implicitly[TagK[Identity]], implicitly[izumi.distage.modules.DefaultModule[Identity]])
  }
  def main(args: Array[String]): Unit = {
    if (args.contains("parallel")) parallelMemoized() else sequentialSingle()
  }

  private def configJson = parse("""{"spike":{"value":42}}""").fold(throw _, identity).asObject.getOrElse(throw new IllegalStateException("configuration must be a JSON object"))

  /** Four tests in two suites, all parallelism levels unlimited, one memoized Lifecycle resource shared by every test. */
  private def parallelMemoized(): Unit = {
    val config = TestConfig(
      pluginConfig = PluginConfig.const(new PluginDef {
        make[Value].fromValue(Value(42))
        make[Shared].fromResource(Lifecycle.make[Identity, Shared](Shared(Counters.acquired.incrementAndGet()))(_ => { Counters.released.incrementAndGet(); () }))
      }),
      configOverrides = Some(AppConfig.provided(configJson)),
      memoizationRoots = Set(DIKey[Shared]),
      parallelEnvs = TestConfig.Parallelism.Unlimited,
      parallelSuites = TestConfig.Parallelism.Unlimited,
      parallelTests = TestConfig.Parallelism.Unlimited,
    )
    val environment = new Environment().create(config)
    val tests = for {
      suiteName <- Seq("ParallelA", "ParallelB")
      testName <- Seq("first", "second")
    } yield {
      val suite = SuiteId(suiteName)
      DistageTest[Identity](
        Functoid { (shared: Shared, value: Value, loaded: AppConfig) =>
          assert(shared.acquisition == 1 && value.n == 42)
          assert(loaded.config.toJson.hcursor.downField("spike").get[Int]("value") == Right(42))
          Counters.threads.add(Thread.currentThread().getName)
          println(s"PARALLEL_BODY $suiteName/$testName thread=${Thread.currentThread().getName}")
        },
        environment,
        TestMeta(TestId(Seq(testName), suite), SourceFilePosition("PortabilityMain.scala", 1), 1L),
        SuiteMeta(suite, suiteName, s"izumi.distage.$suiteName"),
      )
    }
    val reporter = new RecordingReporter
    val result = TestkitRunnerModule.run[Identity](reporter, _ => false, tests.map(_.asInstanceOf[DistageTest[AnyF]]), Nil)
    val summary = s"success=${reporter.successes}, failure=${reporter.failures}, ended=${reporter.ended}, acquired=${Counters.acquired.get()}, released=${Counters.released.get()}, threads=${Counters.threads.size()}, result=$result"
    assert(reporter.successes == 4 && reporter.failures == 0 && reporter.ended && Counters.acquired.get() == 1 && Counters.released.get() == 1, summary)
    println(s"PARALLEL_MEMOIZED_PASS $summary")
  }

  private def sequentialSingle(): Unit = {
    val json = parse("""{"spike":{"value":42}}""").fold(throw _, identity).asObject.getOrElse(throw new IllegalStateException("configuration must be a JSON object"))
    val config = TestConfig(
      pluginConfig = PluginConfig.const(new PluginDef { make[Value].fromValue(Value(42)) }),
      configOverrides = Some(AppConfig.provided(json)),
      parallelEnvs = TestConfig.Parallelism.Sequential,
      parallelSuites = TestConfig.Parallelism.Sequential,
      parallelTests = TestConfig.Parallelism.Sequential,
    )
    val environment = new Environment().create(config)
    val suite = SuiteId("portability")
    val test = DistageTest[Identity](
      Functoid { (value: Value, loaded: AppConfig) =>
        assert(value.n == 42)
        assert(loaded.config.toJson.hcursor.downField("spike").get[Int]("value") == Right(42))
        println("DI_AND_CONFIGURATION_TEST_BODY_EXECUTED")
      },
      environment,
      TestMeta(TestId(Seq("DI and configuration"), suite), SourceFilePosition("PortabilityMain.scala", 1), 1L),
      SuiteMeta(suite, "Portability", "izumi.distage.PortabilityMain"),
    )
    val reporter = new RecordingReporter
    val result = TestkitRunnerModule.run[Identity](reporter, _ => false, Seq(test.asInstanceOf[DistageTest[AnyF]]), Nil)
    assert(reporter.successes == 1 && reporter.failures == 0 && reporter.ended, s"success=${reporter.successes}, failure=${reporter.failures}, ended=${reporter.ended}, result=$result")
    println("TESTPLANNER_AND_DISTAGETESTRUNNER_PASS")
  }
}
