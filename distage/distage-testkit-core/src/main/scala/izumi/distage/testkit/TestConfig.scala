package izumi.distage.testkit

import distage.{BootstrapModule, DIKey, Module}
import izumi.distage.framework.model.PluginSource
import izumi.distage.model.definition
import izumi.distage.model.definition.{Activation, StandardAxis}

/**
  * @param pluginSource    If `None`, scans recursively scans packages of the test class itself
  * @param activation      Chosen configurations
  * @param memoizedKeys    Setting `memoizedKeys` create a distinct memoization group
  *                        the exact same `memoizedKeys`
  * @param moduleOverrides Using overrides will create a distinct memoization group
  *                        for this test, i.e. objects will be memoized only between
  *                        tests with the exact same overrides
  */
final case class TestConfig(
                             pluginSource: Option[PluginSource] = None
                           , activation: Activation = StandardAxis.testProdActivation
                           , memoizedKeys: Set[DIKey] = Set.empty
                           , moduleOverrides: Module = Module.empty
                           , bootstrapOverrides: BootstrapModule = definition.BootstrapModule.empty
                           )
