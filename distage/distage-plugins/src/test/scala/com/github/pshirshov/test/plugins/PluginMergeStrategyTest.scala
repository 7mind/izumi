package com.github.pshirshov.test.plugins

import com.github.pshirshov.izumi.distage.fixtures.BasicCases.BasicCase5
import com.github.pshirshov.izumi.distage.plugins.merge.ConfigurablePluginMergeStrategy.PluginMergeConfig
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
          import com.github.pshirshov.izumi.distage.model.definition.BindingTag.Expressions._
          t"bad"
        }
      ))

      val definition = mergeStrategy.merge(Seq(plugin1, plugin2)).definition
      assert(Injector.Standard().produce(definition).get[TestImpl1].justASet == Set.empty)
    }

    "Preserve identical binding from multiple plugins when one of the plugins was filtered out by tag" in {
      import BasicCase5._

      val plugin1 = new PluginDef {
        tag("good")
        make[TestDependency]
      }

      val plugin2 = new PluginDef {
        tag("bad")
        make[TestDependency]
      }

      val mergeStrategy = new ConfigurablePluginMergeStrategy(PluginMergeConfig(
        disabledTags = {
          import com.github.pshirshov.izumi.distage.model.definition.BindingTag.Expressions._
          t"bad"
        }
      ))

      val definition = mergeStrategy.merge(Seq(plugin1, plugin2)).definition
      assert(Injector.Standard().produce(definition).get[TestDependency] != null)
    }
  }
}
