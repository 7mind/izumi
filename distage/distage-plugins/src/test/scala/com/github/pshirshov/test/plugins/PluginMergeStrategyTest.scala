package com.github.pshirshov.test.plugins

import com.github.pshirshov.izumi.distage.fixtures.BasicCases.BasicCase5
import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.plugins.merge.ConfigurablePluginMergeStrategy.PluginMergeConfig
import com.github.pshirshov.izumi.fundamentals.tags.TagExpr
import distage.Injector
import distage.plugins.{ConfigurablePluginMergeStrategy, PluginDef}
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
        {
          import TagExpr.Strings._
          t"bad"
        }
      ))

      val definition = mergeStrategy.merge(Seq(plugin1, plugin2)).definition
      assert(Injector().produce(definition).get[TestImpl1].justASet == Set.empty)
    }
  }
}
