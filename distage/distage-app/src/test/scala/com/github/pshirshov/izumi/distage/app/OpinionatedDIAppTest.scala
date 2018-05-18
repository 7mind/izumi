package com.github.pshirshov.izumi.distage.app

import com.github.pshirshov.izumi.distage.config.ConfigModule
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.{Locator, reflection}
import com.github.pshirshov.izumi.distage.planning.AssignableFromAutoSetHook
import com.github.pshirshov.izumi.distage.plugins._
import com.github.pshirshov.izumi.logstage.api.TestSink
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import com.github.pshirshov.izumi.logstage.api.routing.LoggingMacroTest
import com.github.pshirshov.test.testapp._
import com.typesafe.config.ConfigFactory
import org.scalatest.WordSpec

class TestAppLauncher(modules: Seq[ModuleDef], pluginMergeConfig: PluginMergeConfig, callback: Locator => Unit) extends OpinionatedDiApp {
  override protected def start(context: Locator): Unit = callback(context)

  val testSink = new TestSink()


  override protected def bootstrapModules: Seq[ModuleDef] = modules

  override protected def router: LogRouter = {
    LoggingMacroTest.mkRouter(testSink)
  }

  val bootstrapConfig: PluginConfig = PluginConfig(debug = false
    , Seq("com.github.pshirshov.izumi")
    , Seq.empty
  )

  val appConfig: PluginConfig = PluginConfig(debug = false
    , Seq(classOf[TestApp].getPackage.getName)
    , Seq.empty
  )

  override protected def requiredComponents: Set[reflection.universe.RuntimeDIUniverse.DIKey] = Set(
    RuntimeDIUniverse.DIKey.get[TestApp]
  )

  override protected def handler: AppFailureHandler = {
    NullHandler
  }

  override protected def mergeStrategy: PluginMergeStrategy[LoadedPlugins] = {
    new ConfigurablePluginMergeStrategy(pluginMergeConfig)
  }
}


class CustomizationModule extends ModuleDef {
  many[PlanningHook]
    .add(new AssignableFromAutoSetHook[Conflict])
}


class OpinionatedDIAppTest extends WordSpec {

  "DI app" should {
    "support dynamic app loading" in {
      val config = AppConfig(ConfigFactory.load())
      val modules = Seq(
        new ConfigModule(config)
        , new CustomizationModule
      )

      val pluginMergeConfig = PluginMergeConfig(
        disabledImplementations = Set(classOf[DisabledImpl].getName, classOf[DisabledBinding].getName)
        , disabledTags = Set("badtag")
        , disabledKeys = Set(classOf[DisabledTrait].getName)
        , preferences = Map(classOf[Conflict].getSimpleName -> BindingPreference(Some("B"), None))
      )

      val app = new TestAppLauncher(modules, pluginMergeConfig, {
        context =>
          assert(context.find[TestApp].nonEmpty)
          assert(context.find[BadApp].isEmpty)
          assert(context.find[DisabledTrait].isEmpty)
          assert(context.find[DisabledBinding].isEmpty)
          assert(context.find[Conflict].exists(_.isInstanceOf[ConflictB]))

          assert(context.get[TestApp].config.value == "test")
          assert(context.get[TestApp].setTest.size == 1)

          ()
      })

      app.main(Array.empty)
    }
  }

}



// TODO : Remove after
object StackOverflowSample1 extends OpinionatedDiApp {
  override protected def start(context: Locator): Unit = {
    context.get[Test]
  }


  val testSink = new TestSink()

  override protected def bootstrapModules: Seq[ModuleDef] = Seq.empty

  override protected def router: LogRouter = {
    LoggingMacroTest.mkRouter(testSink)
  }

  val bootstrapConfig: PluginConfig = PluginConfig(debug = false
    , Seq("com.github.pshirshov.izumi")
    , Seq.empty
  )

  val appConfig: PluginConfig = PluginConfig(debug = false
    , Seq(classOf[Test].getPackage.getName)
    , Seq.empty
  )

  override protected def requiredComponents: Set[reflection.universe.RuntimeDIUniverse.DIKey] = Set(
    RuntimeDIUniverse.DIKey.get[Test]
  )
}

// TODO : impact on stack overflow
class Plugin extends PluginDef {
  make[Test]
}

case class Valid(really : Boolean)
case class Test (valid : Valid)


// TODO : Remove after
object StackOverflowSample2 extends OpinionatedDiApp {
  override protected def start(context: Locator): Unit = {
    context.get[Test]
  }


  val testSink = new TestSink()

  override protected def bootstrapModules: Seq[ModuleDef] = Seq.empty

  override protected def router: LogRouter = {
    LoggingMacroTest.mkRouter(testSink)
  }

  val bootstrapConfig: PluginConfig = PluginConfig(debug = false
    , Seq("com.github.pshirshov.izumi")
    , Seq.empty
  )

  val appConfig: PluginConfig = PluginConfig(debug = false
//    , Seq(classOf[Test].getPackage.getName)
    , Seq.empty
    , Seq.empty
  )

  override protected def requiredComponents: Set[reflection.universe.RuntimeDIUniverse.DIKey] = Set(
    RuntimeDIUniverse.DIKey.get[Test]
  )
}
