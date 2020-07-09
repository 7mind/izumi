package izumi.distage.roles.test

import izumi.distage.plugins.StaticPluginScanner
import org.scalatest.wordspec.AnyWordSpec

class StaticPluginScannerTest extends AnyWordSpec {
  "Prepopulate plugins list in compile time" in {
    val plugins = StaticPluginScanner.staticallyAvailablePlugins("com.github.pshirshov.test.plugins")
    assert(plugins.size == 3)
  }
}
