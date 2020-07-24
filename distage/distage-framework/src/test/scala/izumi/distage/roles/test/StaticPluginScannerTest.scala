package izumi.distage.roles.test

import com.github.pshirshov.test.plugins.{DependingPlugin, EmptyTestPlugin, StaticTestPlugin}
import izumi.distage.plugins.{PluginConfig, StaticPluginScanner}
import izumi.distage.plugins.load.PluginLoader
import org.scalatest.wordspec.AnyWordSpec

class StaticPluginScannerTest extends AnyWordSpec {

  "Static plugin scanner" should {

    "Prepopulate plugins list in compile time" in {
      val plugins = StaticPluginScanner.staticallyAvailablePlugins("com.github.pshirshov.test.plugins")
      assert(plugins.size == 5)
      assert(
        plugins.map(_.getClass).toSet == Set(
          EmptyTestPlugin.getClass,
          classOf[StaticTestPlugin],
          classOf[DependingPlugin],
          classOf[DependingPlugin.NestedDoublePlugin],
          DependingPlugin.NestedDoublePlugin.getClass,
        )
      )
    }

    "Prepopulate plugins list in compile time (PluginConfig)" in {
      val plugins = PluginLoader().load(PluginConfig.staticallyAvailablePlugins("com.github.pshirshov.test.plugins"))
      assert(plugins.size == 5)
      assert(
        plugins.map(_.getClass).toSet == Set(
          EmptyTestPlugin.getClass,
          classOf[StaticTestPlugin],
          classOf[DependingPlugin],
          classOf[DependingPlugin.NestedDoublePlugin],
          DependingPlugin.NestedDoublePlugin.getClass,
        )
      )
    }

  }
}
