package izumi.distage.testkit

import distage.{Activation, BootstrapModule, DIKey, Module, StandardAxis}
import izumi.distage.framework.model.PluginSource
import izumi.distage.plugins.PluginConfig

/**
  * @param pluginSource       Source of module definitions from which to build object graphs for each tests.
  *                           Each [[PluginSource]] creates a distinct memoization group (aka [[izumi.distage.testkit.services.dstest.TestEnvironment]]).
  *                           objects will be memoized only between tests in the same memoization group
  *
  * @param activation         Chosen configurations. Different [[Activation]]s will create distinct memoization groups
  *
  * @param memoizationRoots   Every distinct set of `memoizationRoots` will create a distinct memoization group
  *                           for tests with the exact same `memoizationRoots`
  *
  * @param moduleOverrides    Override loaded plugins with a given [[Module]]. Using overrides
  *                           will create a distinct memoization group, i.e. objects will be
  *                           memoized only between tests with the exact same overrides
  *
  * @param bootstrapOverrides Same as [[moduleOverrides]], but for [[BootstrapModule]]
  */
final case class TestConfig(
                             pluginSource: PluginSource,
                             activation: Activation = StandardAxis.testProdActivation,
                             memoizationRoots: Set[DIKey] = Set.empty,
                             moduleOverrides: Module = Module.empty,
                             bootstrapOverrides: BootstrapModule = BootstrapModule.empty,
                           )

object TestConfig {
  def forSuite(clazz: Class[_]): TestConfig = TestConfig(PluginSource(PluginConfig.cached(Seq(clazz.getPackage.getName))))
}
