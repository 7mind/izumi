package izumi.distage.testkit.spec

import distage.{DIKey, Functoid, Lifecycle, TagK}
import io.circe.{Json, JsonObject}
import izumi.distage.config.ConfigModuleDef
import izumi.distage.config.model.AppConfig
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.{PluginConfig, PluginDef}
import izumi.distage.plugins.load.PluginLoaderDefaultImpl
import izumi.distage.plugins.merge.SimplePluginMergeStrategy
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.testkit.model.*
import izumi.distage.testkit.runner.TestkitRunnerModule
import izumi.distage.testkit.runner.api.TestReporter
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.fundamentals.platform.language.SourceFilePosition
import izumi.fundamentals.platform.language.types.HigherKindedAny.AnyF
import logstage.Log

import java.util.concurrent.{ConcurrentHashMap, CyclicBarrier, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.unused

object NativeTestkitFixtures {
  private final val ParallelTests = 4
  private final val BarrierTimeoutSeconds = 30L
  private final val ReferenceNumber = 42
  private final val OverrideNumber = 53

  def main(args: Array[String]): Unit = {
    configuration()
    memoizedParallel()
    SessionEnvironmentFixtures.main(args)
  }

  private[spec] def configuration(): Unit = {
    val config = TestConfig(
      pluginConfig = PluginConfig.const(new PluginDef with ConfigModuleDef {
        makeConfig[NativeEngineSettings]("engine")
      }),
      configBaseName = "native-testkit-engine",
      parallelEnvs = TestConfig.Parallelism.Sequential,
      parallelSuites = TestConfig.Parallelism.Sequential,
      parallelTests = TestConfig.Parallelism.Sequential,
      logLevel = Log.Level.Error,
    )
    val suite = SuiteId("native-engine-config")
    val test = DistageTest[Identity](
      Functoid {
        (settings: NativeEngineSettings) =>
          require(settings == NativeEngineSettings("héllo", ReferenceNumber), settings.toString)
          println("NATIVE_TESTKIT_DI_CONFIGURATION_BODY")
      },
      environment(config),
      TestMeta(TestId(Vector("typed JSON configuration"), suite), SourceFilePosition("NativeTestkitFixtures.scala", 1), 1L),
      SuiteMeta(suite, "Native engine configuration", "izumi.distage.testkit.spec.NativeTestkitFixtures"),
    )
    run(Vector(test))
    println("NATIVE_TESTKIT_DI_CONFIGURATION_OK")
  }

  private[spec] def memoizedParallel(): Unit = {
    val record = new NativeEngineRecord
    val barrier = new CyclicBarrier(ParallelTests)
    val config = TestConfig(
      pluginConfig = PluginConfig.const(new PluginDef with ConfigModuleDef {
        makeConfig[NativeEngineSettings]("engine")
        make[NativeEngineShared].fromResource(Lifecycle.make[Identity, NativeEngineShared] {
          new NativeEngineShared(record.acquired.incrementAndGet())
        } {
          _ =>
            require(record.entered.get() == ParallelTests, "Memoized resource released before every test body entered")
            record.released.incrementAndGet().discard()
        })
      }),
      memoizationRoots = Set(DIKey[NativeEngineShared]),
      configBaseName = "native-testkit-engine",
      configOverrides = Some(AppConfig.provided(JsonObject("engine" -> Json.obj("number" -> Json.fromInt(OverrideNumber))))),
      parallelEnvs = TestConfig.Parallelism.Unlimited,
      parallelSuites = TestConfig.Parallelism.Unlimited,
      parallelTests = TestConfig.Parallelism.Unlimited,
      logLevel = Log.Level.Error,
    )
    val env = environment(config)
    val tests = Vector.tabulate(ParallelTests) {
      index =>
        val suite = SuiteId("native-engine-parallel-" + (index / 2))
        DistageTest[Identity](
          Functoid {
            (shared: NativeEngineShared, settings: NativeEngineSettings) =>
              require(shared.acquisition == 1 && record.acquired.get() == 1 && record.released.get() == 0)
              require(settings == NativeEngineSettings("héllo", OverrideNumber), settings.toString)
              record.entered.incrementAndGet().discard()
              barrier.await(BarrierTimeoutSeconds, TimeUnit.SECONDS).discard()
              require(record.entered.get() == ParallelTests && record.released.get() == 0)
              println("NATIVE_TESTKIT_PARALLEL_BODY index=" + index)
          },
          env,
          TestMeta(TestId(Vector("parallel " + index), suite), SourceFilePosition("NativeTestkitFixtures.scala", 1), index.toLong),
          SuiteMeta(suite, suite.suiteId, "izumi.distage.testkit.spec.NativeTestkitFixtures"),
        )
    }
    run(tests)
    require(record.acquired.get() == 1 && record.released.get() == 1 && record.entered.get() == ParallelTests)
    println("NATIVE_TESTKIT_PARALLEL_MEMOIZED_OK tests=" + ParallelTests + " acquired=1 released=1")
  }

  private def environment(config: TestConfig): TestEnvironment = {
    new SessionTestEnvironment(new TestEnvironmentFactory.Impl).load[Identity](
      config,
      new PluginLoaderDefaultImpl,
      RolesInfo(Set.empty, Set.empty, Set.empty, Set.empty, Set.empty, Set.empty),
      SimplePluginMergeStrategy,
      TagK[Identity],
      implicitly[DefaultModule[Identity]],
    )
  }

  private def run(tests: Vector[DistageTest[Identity]]): Unit = {
    val reporter = new NativeEngineReporter
    val results = TestkitRunnerModule.run[Identity](reporter, _ => false, tests.map(_.asInstanceOf[DistageTest[AnyF]]), Nil)
    val successes = results.flatMap {
      case EnvResult.EnvSuccess(_, groups) => groups.flatMap {
        case GroupResult.GroupSuccess(outputs, _) => outputs.collect { case result: IndividualTestResult.TestSuccess => result.test.test.id }
        case _ => Vector.empty
      }
      case _ => Vector.empty
    }
    val expected = tests.map(_.testMeta.id).toSet
    require(successes.size == tests.size && successes.toSet == expected, results.toString)
    require(reporter.started.get() == 1 && reporter.ended.get() == 1 && reporter.failures.get() == 0)
    require(reporter.successes.size() == tests.size && expected.forall(reporter.successes.contains))
  }
}

final case class NativeEngineSettings(label: String, number: Int)
final class NativeEngineShared(val acquisition: Int)

final class NativeEngineRecord {
  val acquired = new AtomicInteger(0)
  val released = new AtomicInteger(0)
  val entered = new AtomicInteger(0)
}

final class NativeEngineReporter extends TestReporter {
  val successes = ConcurrentHashMap.newKeySet[TestId]()
  val started = new AtomicInteger(0)
  val ended = new AtomicInteger(0)
  val failures = new AtomicInteger(0)

  override def beginScope(@unused id: ScopeId): Unit = started.incrementAndGet().discard()
  override def endScope(@unused id: ScopeId): Unit = ended.incrementAndGet().discard()
  override def beginLevel(@unused scope: ScopeId, @unused depth: Int, @unused suites: List[SuiteMeta]): Unit = ()
  override def endLevel(@unused scope: ScopeId, @unused depth: Int, @unused suites: List[SuiteMeta]): Unit = ()
  override def beginSuite(@unused scopeId: ScopeId, @unused depth: Int, @unused suiteMeta: SuiteMeta): Unit = ()
  override def endSuite(@unused scopeId: ScopeId, @unused depth: Int, @unused suiteMeta: SuiteMeta): Unit = ()
  override def testSetupStatus(@unused scopeId: ScopeId, @unused depth: Int, meta: FullMeta, testStatus: TestStatus.Setup): Unit = {
    failures.incrementAndGet().discard()
    println("NATIVE_TESTKIT_SETUP_FAILURE " + meta + " " + testStatus)
  }
  override def testStatus(@unused scope: ScopeId, @unused depth: Int, meta: FullMeta, testStatus: TestStatus): Unit = testStatus match {
    case _: TestStatus.Succeed => require(successes.add(meta.test.id), "Duplicate successful test report: " + meta)
    case _: TestStatus.Done =>
      failures.incrementAndGet().discard()
      println("NATIVE_TESTKIT_TEST_FAILURE " + meta + " " + testStatus)
    case _ => ()
  }
}
