package izumi.distage.testkit.distagesuite.memoized

import com.typesafe.config.ConfigFactory
import distage.{DIKey, Functoid, Injector, ModuleDef, TagK}
import izumi.distage.config.model.AppConfig
import izumi.distage.model.definition.{Activation, BootstrapModuleDef, StandardAxis}
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.{PluginConfig, PluginDef}
import izumi.distage.testkit.distagesuite.memoized.TestEnvMergingTest.*
import izumi.distage.testkit.model.*
import izumi.distage.testkit.model.TestConfig.Parallelism
import izumi.distage.testkit.runner.TestkitRunnerModule
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.impl.TestPlanner
import izumi.distage.testkit.runner.impl.TestPlanner.PreparedTestEnv
import izumi.distage.testkit.runner.impl.services.ParTraverseExt
import izumi.distage.testkit.spec.DistageTestEnv
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.SourceFilePosition
import izumi.fundamentals.platform.language.types.HigherKindedAny.AnyF
import org.scalatest.wordspec.AnyWordSpec

class TestEnvMergingTest extends AnyWordSpec {

  private val baseConfig: TestConfig = TestConfig(
    pluginConfig = PluginConfig.const(new PluginDef {
      make[Memoized].from(Memoized("base"))
      make[NotMemoized].from(NotMemoized("base"))
      make[Unused].from(Unused("base"))
    }),
    memoizationRoots = Set(DIKey[Memoized]),
    activation = Activation.empty,
    activationStrategy = TestActivationStrategy.IgnoreConfig,
  )

  "Test planner" should {
    "merge envs that differ only in bindings outside of the memoized graph" in {
      val other = baseConfig.copy(moduleOverrides = new ModuleDef {
        make[NotMemoized].from(NotMemoized("other"))
      })
      assert(environment(baseConfig) != environment(other))

      assert(planSuites(baseConfig, other) == List(List(Set("suite-0", "suite-1"))))
    }

    "merge envs that differ only in unused memoization roots" in {
      val other = baseConfig.copy(memoizationRoots = baseConfig.memoizationRoots ++ Set(DIKey[Unused]))
      assert(environment(baseConfig) != environment(other))

      assert(planSuites(baseConfig, other) == List(List(Set("suite-0", "suite-1"))))
    }

    "merge envs that differ only in activations that do not affect the memoized graph" in {
      val other = baseConfig.copy(activation = StandardAxis.testDummyActivation)
      assert(environment(baseConfig) != environment(other))

      assert(planSuites(baseConfig, other) == List(List(Set("suite-0", "suite-1"))))
    }

    "merge envs that differ only in test-runner-only parameters" in {
      val other = baseConfig.copy(parallelTests = Parallelism.Sequential, parallelSuites = Parallelism.Sequential, debugOutput = true)
      assert(environment(baseConfig) == environment(other))

      assert(planSuites(baseConfig, other) == List(List(Set("suite-0", "suite-1"))))
    }

    "share the env but split memoization levels when memoized bindings differ" in {
      val other = baseConfig.copy(moduleOverrides = new ModuleDef {
        make[Memoized].from(Memoized("other"))
      })

      assert(planSuites(baseConfig, other) == List(List(Set("suite-0"), Set("suite-1"))))
    }

    "not merge envs with different bootstrap modules" in {
      val other = baseConfig.copy(bootstrapOverrides = new BootstrapModuleDef {
        make[BootstrapMarker].fromValue(BootstrapMarker("other"))
      })

      assert(planSuites(baseConfig, other) == List(List(Set("suite-0")), List(Set("suite-1"))))
    }

    "not merge envs with different configs" in {
      val other = baseConfig.copy(configOverrides = Some(AppConfig.provided(ConfigFactory.parseString("unrelated = 1"))))

      assert(planSuites(baseConfig, other) == List(List(Set("suite-0")), List(Set("suite-1"))))
    }

    "not merge envs with different execution parameters" in {
      val other = baseConfig.copy(parallelEnvs = Parallelism.Sequential)

      assert(planSuites(baseConfig, other) == List(List(Set("suite-0")), List(Set("suite-1"))))
    }
  }

  private def environment(config: TestConfig): TestEnvironment = {
    new DistageTestEnv {}.loadEnvironment(config, TagK[Identity], implicitly[DefaultModule[Identity]])
  }

  private def planSuites(configs: TestConfig*): List[List[Set[String]]] = {
    val tests = configs.zipWithIndex.map {
      case (config, i) =>
        val suiteId = SuiteId(s"suite-$i")
        DistageTest[Identity](
          Functoid((_: Memoized, _: NotMemoized) => ()),
          environment(config),
          TestMeta(TestId(Seq("test"), suiteId), SourceFilePosition.unknown, i.toLong),
          SuiteMeta(suiteId, suiteId.suiteId, suiteId.suiteId),
        ).asInstanceOf[DistageTest[AnyF]]
    }

    val envs = Injector
      .withoutDefaultModule[Identity]()
      .produceRun(new TestkitRunnerModule[Identity](NoopReporter, _ => false)) {
        (planner: TestPlanner, parTraverseExt: ParTraverseExt[Identity]) =>
          val planned = planner.planGroupTests[Identity](tests, parTraverseExt)
          assert(planned.bad.isEmpty)
          planned.good.flatMap(_.envs).toMap[PreparedTestEnv[AnyF], TestTree[AnyF]]
      }

    envs.values.toList
      .map(_.nested.map(_.allTests.map(_.test.suiteMeta.suiteName).toSet).sortBy(_.toList.sorted.mkString))
      .sortBy(_.map(_.toList.sorted.mkString).mkString)
  }
}

object TestEnvMergingTest {
  final case class Memoized(name: String)
  final case class NotMemoized(name: String)
  final case class Unused(name: String)
  final case class BootstrapMarker(name: String)

  object NoopReporter extends TestReporter {
    override def beginScope(id: ScopeId): Unit = ()
    override def endScope(id: ScopeId): Unit = ()
    override def beginLevel(scope: ScopeId, depth: Int, suites: List[SuiteMeta]): Unit = ()
    override def endLevel(scope: ScopeId, depth: Int, suites: List[SuiteMeta]): Unit = ()
    override def beginSuite(scopeId: ScopeId, depth: Int, suiteMeta: SuiteMeta): Unit = ()
    override def endSuite(scopeId: ScopeId, depth: Int, suiteMeta: SuiteMeta): Unit = ()
    override def testSetupStatus(scopeId: ScopeId, depth: Int, meta: FullMeta, testStatus: TestStatus.Setup): Unit = ()
    override def testStatus(scope: ScopeId, depth: Int, meta: FullMeta, testStatus: TestStatus): Unit = ()
  }
}
