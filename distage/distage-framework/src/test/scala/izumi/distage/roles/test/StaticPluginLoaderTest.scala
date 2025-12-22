package izumi.distage.roles.test

import com.github.pshirshov.test.plugins.{DependingPlugin, EmptyTestPlugin, ObjectTestPlugin, StaticTestPlugin, StaticTestPlugin2}
import izumi.distage.plugins.load.PluginLoader
import izumi.distage.plugins.{PluginConfig, StaticPluginLoader}
import org.scalatest.wordspec.AnyWordSpec

class StaticPluginLoaderTest extends AnyWordSpec {

  private val expectedPlugins: Set[Class[?]] = Set[Class[?]](
    EmptyTestPlugin.getClass,
    classOf[StaticTestPlugin],
    classOf[StaticTestPlugin2],
    classOf[DependingPlugin],
    classOf[DependingPlugin.NestedDoublePlugin],
    DependingPlugin.NestedDoublePlugin.getClass,
    ObjectTestPlugin.getClass,
  )

  "Static plugin scanner" should {

    "Prepopulate plugins list in compile time" in {
      val plugins = StaticPluginLoader.scanCompileTime("com.github.pshirshov.test.plugins")
      assert(plugins.size == expectedPlugins.size)
      assert(plugins.map(_.getClass).toSet == expectedPlugins)
    }

    "Prepopulate plugins list in compile time (PluginConfig)" in {
      val plugins = PluginLoader().load(PluginConfig.compileTime("com.github.pshirshov.test.plugins"))
      assert(plugins.size == expectedPlugins.size)
      assert(plugins.result.map(_.getClass).toSet == expectedPlugins)
    }

  }
}
