package com.github.pshirshov.izumi.distage.app

import com.github.pshirshov.izumi.distage.app.macros.{LogstageModuleRequirements, StaticPluginChecker}
import com.github.pshirshov.test.plugins.{DependingPlugin, StaticTestPlugin}
import org.scalatest.WordSpec

class StaticPluginCheckerTest extends WordSpec {
  val pkg = "com.github.pshirshov.test.testapp"

  "Check when config & requirements are valid" in {
    StaticPluginChecker.checkWithConfig[StaticTestPlugin, LogstageModuleRequirements]("x", ".*check-test-good.conf")
  }

  "Check without config" in {
    StaticPluginChecker.check[StaticTestPlugin, LogstageModuleRequirements]("x")
  }

  "Check depending plugin with plugins" in {
    StaticPluginChecker.checkWithPlugins[DependingPlugin, LogstageModuleRequirements]("com.github.pshirshov.test.plugins", "x")
    StaticPluginChecker.checkWithPluginsConfig[DependingPlugin, LogstageModuleRequirements]("com.github.pshirshov.test.plugins", "x", ".*check-test-good.conf")
  }

  "Check with different tag" in {
    StaticPluginChecker.checkWithConfig[StaticTestPlugin, LogstageModuleRequirements]("y", ".*check-test-good.conf")
  }

  "Check with invalid tags" in {
    assertTypeError("""StaticPluginChecker.checkWithConfig[StaticTestPlugin, LogstageModuleRequirements]("", ".*check-test-good.conf")""")
  }

  "Check when config is false" in {
    assertTypeError("""StaticPluginChecker.checkWithConfig[StaticTestPlugin, LogstageModuleRequirements]("x", ".*check-test-bad.conf")""")
  }

  "Check when requirements are false" in {
    assertTypeError("""StaticPluginChecker.checkWithConfig[StaticTestPlugin, NoModuleRequirements]("x", ".*check-test-good.conf")""")
  }

}
