package com.github.pshirshov.test

import com.github.pshirshov.test.plugins.{DependingPlugin, EmptyTestPlugin, StaticTestPlugin}
import distage.plugins.PluginLoader
import izumi.distage.plugins.PluginConfig
import org.scalatest.wordspec.AnyWordSpec

class PluginLoaderTest extends AnyWordSpec {
  "Load plugins list at runtime time" in {
    val plugins = PluginLoader().load(PluginConfig.packages(Seq("com.github.pshirshov.test.plugins")))
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
