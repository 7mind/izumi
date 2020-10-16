package izumi.distage.roles.test

import com.github.pshirshov.test.plugins.{DependingPlugin, EmptyTestPlugin, ObjectTestPlugin, StaticTestPlugin}
import izumi.distage.plugins.{PluginConfig, StaticPluginLoader}
import izumi.distage.plugins.load.PluginLoader
import org.scalatest.wordspec.AnyWordSpec

class StaticPluginLoaderTest extends AnyWordSpec {

  "Static plugin scanner" should {

    "Prepopulate plugins list in compile time" in {
      val plugins = StaticPluginLoader.staticallyAvailablePlugins("com.github.pshirshov.test.plugins")
      assert(plugins.size == 6)
      assert(
        plugins.map(_.getClass).toSet == Set(
          EmptyTestPlugin.getClass,
          classOf[StaticTestPlugin],
          classOf[DependingPlugin],
          classOf[DependingPlugin.NestedDoublePlugin],
          DependingPlugin.NestedDoublePlugin.getClass,
          ObjectTestPlugin.getClass,
        )
      )
    }

    "Prepopulate plugins list in compile time (PluginConfig)" in {
      val plugins = PluginLoader().load(PluginConfig.static("com.github.pshirshov.test.plugins"))
      assert(plugins.size == 6)
      assert(
        plugins.map(_.getClass).toSet == Set(
          EmptyTestPlugin.getClass,
          classOf[StaticTestPlugin],
          classOf[DependingPlugin],
          classOf[DependingPlugin.NestedDoublePlugin],
          DependingPlugin.NestedDoublePlugin.getClass,
          ObjectTestPlugin.getClass,
        )
      )
    }

  }
}
