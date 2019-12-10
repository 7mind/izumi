package izumi.distage.testkit

import distage.{BootstrapModule, DIKey, Module}
import izumi.distage.framework.model.PluginSource
import izumi.distage.model.definition
import izumi.distage.model.definition.{Activation, StandardAxis}

/**
  * @param pluginSource       If `None`, recursively scans packages of the test class itself
  *                           each pluginSource creates a distinct memoization group, i.e.
  *                           objects will be memoized only between tests with the same plugins
  * @param activation         Chosen configurations. Different Activations have distinct memoization groups
  * @param memoizedKeys       Setting `memoizedKeys` will create a distinct memoization group
  *                           of tests with the exact same `memoizedKeys`
  * @param moduleOverrides    Override loaded plugins with a given [[Module]]. Using overrides
  *                           will create a distinct memoization group, i.e. objects will be
  *                           memoized only between tests with the exact same overrides
  * @param bootstrapOverrides Same as [[moduleOverrides]], but for [[BootstrapModule]]
  */
final case class TestConfig(
                             pluginSource: Option[PluginSource] = None,
                             activation: Activation = StandardAxis.testProdActivation,
                             memoizedKeys: Set[DIKey] = Set.empty,
                             moduleOverrides: Module = Module.empty,
                             bootstrapOverrides: BootstrapModule = definition.BootstrapModule.empty,
                           )
