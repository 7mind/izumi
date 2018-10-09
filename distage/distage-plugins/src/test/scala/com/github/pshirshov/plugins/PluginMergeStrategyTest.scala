package com.github.pshirshov.plugins

import distage.plugins.{ConfigurablePluginMergeStrategy, PluginDef}
import com.github.pshirshov.izumi.distage.fixtures.BasicCases._
import com.github.pshirshov.izumi.distage.plugins.merge.ConfigurablePluginMergeStrategy.PluginMergeConfig
import com.github.pshirshov.izumi.fundamentals.tags.TagExpr
import distage.Injector
import org.scalatest.WordSpec

class PluginMergeStrategyTest extends WordSpec {

  "Plugin merge strategy" should {
    "Preserve empty set binding with multiple conflicting tags" in {
      import BasicCase5._

      val plugin1 = new PluginDef {
        many[TestDependency]
        make[TestImpl1]
      }

      val plugin2 = new PluginDef {
        tag("bad")
        many[TestDependency]
          .add[TestDependency]
      }

      val mergeStrategy = new ConfigurablePluginMergeStrategy(PluginMergeConfig(
        TagExpr.Strings.Has("bad")
      ))

      val definition = mergeStrategy.merge(Seq(plugin1, plugin2)).definition

      assert(Injector().produce(definition).get[TestImpl1].justASet == Set.empty)
    }
  }
}
