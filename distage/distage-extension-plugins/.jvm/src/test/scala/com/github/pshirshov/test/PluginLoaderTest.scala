package com.github.pshirshov.test

import com.github.pshirshov.test.plugins.{DependingPlugin, EmptyTestPlugin, ObjectTestPlugin, StaticTestPlugin, StaticTestPlugin2}
import distage.plugins.PluginLoader
import izumi.distage.plugins.PluginConfig
import org.scalatest.wordspec.AnyWordSpec

class PluginLoaderTest extends AnyWordSpec {
  "Load plugins list at runtime time" in {
    val plugins = PluginLoader().load(PluginConfig.packages(Seq("com.github.pshirshov.test.plugins")))
    val expected = Set[Class[?]](
      EmptyTestPlugin.getClass,
      classOf[StaticTestPlugin],
      classOf[StaticTestPlugin2],
      classOf[DependingPlugin],
      classOf[DependingPlugin.NestedDoublePlugin],
      DependingPlugin.NestedDoublePlugin.getClass,
      ObjectTestPlugin.getClass,
    )
    assert(plugins.size == expected.size)
    assert(plugins.result.map(_.getClass).toSet == expected)
  }
}
