package izumi.distage.testkit

import distage.config.AppConfig
import distage.{Activation, BootstrapModule, DIKey, Module, StandardAxis}
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.plugins.PluginConfig
import izumi.logstage.api.Log

/**
  * General options:
  *
  * @param pluginConfig          Source of module definitions from which to build object graphs for each tests.
  *                              Each [[PluginConfig]] creates a distinct memoization group (aka [[izumi.distage.testkit.services.dstest.TestEnvironment]]).
  *                              Components specified in `memoizationRoots` will be memoized only for the tests in the same memoization group.
  *
  * @param bootstrapPluginConfig Same as [[pluginConfig]], but for [[BootstrapModule]]
  *
  * @param activation            Chosen activation axes. Different [[Activation]]s will create distinct memoization groups
  *
  * @param memoizationRoots      Specifies the components that will be created *once* and shared across all tests within
  *                              the same memoization group (i.e. with the same [[TestConfig]])
  *                              Every distinct set of `memoizationRoots` will create a distinct memoization group

  * @param forcedRoots           Specifies components that will be treated as if they are a dependency of every test within
  *                              this memoization group. They will not be garbage collected even if no other object or test
  *                              declares a dependency on them components. When combined with `memoizationRoots`, a [[distage.DIResource]]
  *                              binding can implement global start/stop lifecycle for all tests within this memoization group.
  *                              Every distinct set of `forcedRoots` will create a distinct memoization group
  *
  * @param moduleOverrides       Override loaded plugins with a given [[Module]]. Using overrides
  *                              will create a distinct memoization group, i.e. objects will be
  *                              memoized only between tests with the exact same overrides
  *
  * @param bootstrapOverrides    Same as [[moduleOverrides]], but for [[BootstrapModule]]
  *
  *
  * Parallelism options:
  *
  *
  * @param parallelEnvs          Whether to run distinct memoization groups in parallel, default: `true`. Sequential envs will run in sequence after the parallel ones.
  * @param parallelSuites        Whether to run test suites in parallel, default: `true`.
  * @param parallelTests         Whether to run test cases in parallel, default: `true`.
  *
  *
  * Other options:
  *
  *
  * @param configBaseName        Search for config in HOCON resource files with names `$configBaseName.conf`,
  *                              `$configBaseName-reference.conf`, `$configBaseName-reference-dev.conf`
  *                              (see [[izumi.distage.framework.services.ConfigLoader]]
  *
  * @param configOverrides       Overriding definitions on top of main loaded config, default `None`
  * @param planningOptions       [[PlanningOptions]], debug options for [[distage.Planner]]
  * @param testRunnerLogLevel    Verbosity of [[services.dstest.DistageTestRunner]] log messages, default: `Info`
  *
  */
final case class TestConfig( // general options
                             pluginConfig: PluginConfig,
                             bootstrapPluginConfig: PluginConfig = PluginConfig.empty,
                             activation: Activation = StandardAxis.testProdActivation,
                             memoizationRoots: Set[_ <: DIKey] = Set.empty,
                             forcedRoots: Set[_ <: DIKey] = Set.empty,
                             moduleOverrides: Module = Module.empty,
                             bootstrapOverrides: BootstrapModule = BootstrapModule.empty,

                             // parallelism options
                             parallelEnvs: Boolean = true,
                             parallelSuites: Boolean = true,
                             parallelTests: Boolean = true,

                             // other options
                             configBaseName: String,
                             configOverrides: Option[AppConfig] = None,
                             planningOptions: PlanningOptions = PlanningOptions(),
                             testRunnerLogLevel: Log.Level = Log.Level.Info,
                           )
object TestConfig {
  def forSuite(clazz: Class[_]): TestConfig = {
    val packageName = clazz.getPackage.getName
    TestConfig(
      pluginConfig = PluginConfig.cached(Seq(packageName)),
      configBaseName = s"${packageName.split('.').last}-test",
    )
  }
}
