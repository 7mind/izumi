//package izumi.distage.roles.test
//
//import izumi.distage.staticinjector.plugins.{LogstageModuleRequirements, StaticPluginChecker}
//import com.github.pshirshov.test.plugins.{DependingPlugin, StaticTestPlugin}
//import org.scalatest.WordSpec
//
//class StaticPluginCheckerTest extends WordSpec {
//
//  "Check when config & requirements are valid" in {
//    StaticPluginChecker.checkWithConfig[StaticTestPlugin, LogstageModuleRequirements]("test:x", ".*check-test-good.conf")
//  }
//
//  "Check without config" in {
//    StaticPluginChecker.check[StaticTestPlugin, LogstageModuleRequirements]("test:x")
//  }
//
//  "Check depending plugin with plugins" in {
//    StaticPluginChecker.checkWithPlugins[DependingPlugin, LogstageModuleRequirements]("com.github.pshirshov.test.plugins", "test:x")
//    StaticPluginChecker.checkWithPluginsConfig[DependingPlugin, LogstageModuleRequirements]("com.github.pshirshov.test.plugins", "test:x", ".*check-test-good.conf")
//  }
//
//  "Check with different tag" in {
//    StaticPluginChecker.checkWithConfig[StaticTestPlugin, LogstageModuleRequirements]("test:y", ".*check-test-good.conf")
//  }
//
//  "Check with invalid tags" in {
//    assertTypeError("""StaticPluginChecker.checkWithConfig[StaticTestPlugin, LogstageModuleRequirements]("missing:tag", ".*check-test-good.conf")""")
//  }
//
//  "Check when config is false" in {
//    assertTypeError("""StaticPluginChecker.checkWithConfig[StaticTestPlugin, LogstageModuleRequirements]("test:x", ".*check-test-bad.conf")""")
//  }
//
//  "Check when requirements are false" in {
//    assertTypeError("""StaticPluginChecker.checkWithConfig[StaticTestPlugin, NoModuleRequirements]("test:x", ".*check-test-good.conf")""")
//  }
//
//}
