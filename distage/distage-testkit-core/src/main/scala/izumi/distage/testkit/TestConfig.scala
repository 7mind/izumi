package izumi.distage.testkit

import distage._
import distage.config.AppConfig
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.model.definition.Axis.AxisValue
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.TestConfig.{ParallelLevel, TaggedKeys}
import izumi.distage.testkit.services.dstest.BootstrapFactory
import izumi.logstage.api.Log

import scala.language.implicitConversions

/**
  * General options:
  *
  *
  * @param pluginConfig          Source of module definitions from which to build object graphs for each test.
  *                              Changes to [[PluginConfig]] that alter implementations of components in [[memoizationRoots]]
  *                              OR their dependencies will cause the test to execute in a new memoization environment,
  *                              check the initial log output in tests for information about the memoization environments in your tests.
  *                              Components specified in `memoizationRoots` will be memoized only for the tests in the same memoization environment.
  *
  * @param bootstrapPluginConfig Same as [[pluginConfig]], but for [[BootstrapModule]].
  *                              Every distinct `bootstrapPluginConfig` will create a distinct memoization environment.
  *
  * @param activation            Chosen activation axes. Changes in [[Activation]] that alter implementations of components in [[memoizationRoots]]
  *                              OR their dependencies will cause the test to execute in a new memoization environment,
  *                              check the initial log output in tests for information about the memoization environments in your tests.
  *
  * @param memoizationRoots      Specifies the components that will be created *once* and shared across all tests within
  *                              the same memoization environment (i.e. with the same [[TestConfig]])
  *                              Every distinct set of `memoizationRoots` will create a distinct memoization environment
  *
  * @param forcedRoots           Specifies components that will be treated as if they are a dependency of every test within
  *                              this memoization environment. Components designated as forced roots will not be garbage
  *                              collected even if there are no components or tests that depend on them.
  *                              When combined with `memoizationRoots`, a [[distage.DIResource]] binding can be used to
  *                              implement a global start/stop action (beforeAll/afterAll) for all tests within this memoization environment.
  *                              Changes to `forcedRoots` that alter implementations of components in [[memoizationRoots]]
  *                              OR their dependencies will cause the test to execute in a new memoization environment,
  *                              check the initial log output in tests for information about the memoization environments in your tests.
  *
  * @param moduleOverrides       Override loaded plugins with a given [[Module]]. As long as overriden bindings are not memoized,
  *                              or dependencies of memoized components, using overrides will NOT create a new memoization environment.
  *                              Changes to `moduleOverrides` that alter implementations of components in [[memoizationRoots]]
  *                              OR their dependencies will cause the test to execute in a new memoization environment,
  *                              check the initial log output in tests for information about the memoization environments in your tests.
  *
  * @param bootstrapOverrides    Same as [[moduleOverrides]], but for [[BootstrapModule]]
  *                              Every distinct `bootstrapPluginConfig` will create a distinct memoization environment.
  *
  *
  * Parallelism options:
  *
  *
  * @param parallelEnvs          [[ParallelLevel]] of distinct memoization environments run, default: [[ParallelLevel.Unlimited]].
  *                              Sequential envs will run in sequence after the parallel ones.
  *
  * @param parallelSuites        [[ParallelLevel]] of test suites run, default: [[ParallelLevel.Unlimited]].
  *                              Sequential suites will run in sequence after the parallel ones.
  *
  * @param parallelTests         [[ParallelLevel]] of test cases run, default: [[ParallelLevel.Unlimited]].
  *                              Sequential tests will run in sequence after the parallel ones.
  *
  *
  * Other options, Tests with different other options will run in distinct memoization environments:
  *
  *
  * @param configBaseName        Search for config in HOCON resource files with names `$configBaseName.conf`,
  *                              `$configBaseName-reference.conf`, `$configBaseName-reference-dev.conf`
  *                              (see [[izumi.distage.framework.services.ConfigLoader]]
  *
  * @param configOverrides       Overriding definitions on top of main loaded config, default `None`
  *
  * @param bootstrapFactory      [[BootstrapFactory]], controls config loading & initial modules

  * @param planningOptions       [[PlanningOptions]], debug options for [[distage.Planner]]
  *
  * @param logLevel              Log level for the [[logstage.IzLogger]] used in testkit and provided to the tests (will be overriden by plugin / module bindings if exist)
  *
  * @param debugOutput           Print testkit debug messages, including those helping diagnose memoization environment issues,
  *                              default: `false`, also controlled by [[DebugProperties.`izumi.distage.testkit.debug`]] system property
  *
  */
final case class TestConfig(
  // general options
  pluginConfig: PluginConfig,
  bootstrapPluginConfig: PluginConfig = PluginConfig.empty,
  activation: Activation = StandardAxis.testProdActivation,
  moduleOverrides: Module = Module.empty,
  bootstrapOverrides: BootstrapModule = BootstrapModule.empty,
  memoizationRoots: TaggedKeys = TaggedKeys.empty,
  forcedRoots: TaggedKeys = TaggedKeys.empty,
  // parallelism options
  parallelEnvs: ParallelLevel = ParallelLevel.Unlimited,
  parallelSuites: ParallelLevel = ParallelLevel.Unlimited,
  parallelTests: ParallelLevel = ParallelLevel.Unlimited,
  // other options
  configBaseName: String,
  configOverrides: Option[AppConfig] = None,
  bootstrapFactory: BootstrapFactory = BootstrapFactory.Impl,
  planningOptions: PlanningOptions = PlanningOptions(),
  logLevel: Log.Level = Log.Level.Info,
  debugOutput: Boolean = false,
)
object TestConfig {
  def forSuite(clazz: Class[_]): TestConfig = {
    val packageName = clazz.getPackage.getName
    TestConfig(
      pluginConfig = PluginConfig.cached(Seq(packageName)),
      configBaseName = s"${packageName.split('.').last}-test",
    )
  }

  final case class TaggedKeys(keys: Map[Set[_ <: AxisValue], Set[_ <: DIKey]]) {
    @inline def getActiveKeys(activation: Activation): Set[DIKey] = {
      keys
        .filter {
          case (axesValues, _) =>
            axesValues.forall(v => activation.activeChoices.get(v.axis).contains(v))
        }.flatMap(_._2).toSet
    }
    @inline def ++(other: TaggedKeys): TaggedKeys = {
      val allKeys = this.keys.toSeq ++ other.keys.toSeq
      val updatedKeys = allKeys.groupBy(_._1).map { case (k, vals) => k -> vals.flatMap(_._2).toSet }
      TaggedKeys(updatedKeys)
    }
  }
  object TaggedKeys {
    val empty: TaggedKeys = TaggedKeys(Map.empty)
    @inline implicit def fromSet(set: Set[_ <: DIKey]): TaggedKeys = TaggedKeys(Map(Set.empty -> set))
    @inline implicit def fromMap[SA <: Set[_ <: AxisValue], SD <: Set[_ <: DIKey]](map: Map[SA, SD]): TaggedKeys = TaggedKeys(map.map(identity))
  }

  sealed trait ParallelLevel
  object ParallelLevel {
    final case class Fixed(n: Int) extends ParallelLevel
    case object Unlimited extends ParallelLevel
    case object Sequential extends ParallelLevel
  }
}
