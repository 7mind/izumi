package izumi.distage.roles.test

import izumi.distage.staticinjector.plugins.{LogstageModuleRequirements, StaticPluginChecker}
import com.github.pshirshov.test.plugins.{DependingPlugin, StaticTestPlugin}
import izumi.distage.plugins.StaticPluginScanner
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.exceptions.TestFailedException

class StaticPluginCheckerTest extends AnyWordSpec {

  "Check without config" in {
    StaticPluginChecker.check[StaticTestPlugin, LogstageModuleRequirements]("test:x")
  }

  "Check when config & requirements are valid" in {
    StaticPluginChecker.checkWithConfig[StaticTestPlugin, LogstageModuleRequirements]("test:x", ".*check-test-good.conf")
  }

  "Check depending plugin with plugins" in {
    StaticPluginChecker.checkWithPlugins[DependingPlugin, LogstageModuleRequirements]("com.github.pshirshov.test.plugins", "test:x")
    StaticPluginChecker.checkWithPluginsConfig[DependingPlugin, LogstageModuleRequirements]("com.github.pshirshov.test.plugins", "test:x", ".*check-test-good.conf")
  }

  "Check with different tag" in {
    StaticPluginChecker.checkWithConfig[StaticTestPlugin, LogstageModuleRequirements]("test:y", ".*check-test-good.conf")
  }

  "Check with invalid tags" in {
    assertTypeError("""StaticPluginChecker.checkWithConfig[StaticTestPlugin, LogstageModuleRequirements]("missing:tag", ".*check-test-good.conf")""")
  }

  // FIXME: can't parse config ahead of time anymore ???
  // FIXME: should've failed on trying to decode "abc" to Int
  "progression test: can't check when config is false after 0.10" in {
    intercept[TestFailedException] {
      assertTypeError("""StaticPluginChecker.checkWithConfig[StaticTestPlugin, LogstageModuleRequirements]("test:x", ".*check-test-bad.conf")""")
    }
  }

  "Check when requirements are false" in {
    assertTypeError("""StaticPluginChecker.checkWithConfig[StaticTestPlugin, NoModuleRequirements]("test:x", ".*check-test-good.conf")""")
  }

}
