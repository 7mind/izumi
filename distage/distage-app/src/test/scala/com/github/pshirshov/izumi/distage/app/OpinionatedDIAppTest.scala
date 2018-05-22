package com.github.pshirshov.izumi.distage.app

import com.github.pshirshov.izumi.distage.config.ConfigModule
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.definition.{ModuleBase, ModuleDef}
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.planning.AssignableFromAutoSetHook
import com.github.pshirshov.izumi.distage.plugins._
import com.github.pshirshov.izumi.logstage.api.TestSink
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import com.github.pshirshov.izumi.logstage.api.routing.LoggingMacroTest
import com.github.pshirshov.test.testapp._
import com.typesafe.config.ConfigFactory
import org.scalatest.WordSpec

case class EmptyCfg()

class CustomizationModule extends ModuleDef {
  many[PlanningHook]
    .add(new AssignableFromAutoSetHook[Conflict])
}

class TestAppLauncher(callback: (Locator, ApplicationBootstrapStrategy[EmptyCfg]#Context) => Unit) extends OpinionatedDiApp {
  override type CommandlineConfig = EmptyCfg

  override def handler: AppFailureHandler = NullHandler

  val testSink = new TestSink()

  override protected def context(args: Array[String]): Strategy = {
    val bsContext: BootstrapContext = BootstrapContextDefaultImpl(
      EmptyCfg()
      , bootstrapConfig
      , pluginConfig
      , AppConfig(ConfigFactory.load())
    )

    new ApplicationBootstrapStrategyBaseImpl(bsContext) {
      override def mergeStrategy: PluginMergeStrategy[LoadedPlugins] = {
        new ConfigurablePluginMergeStrategy(pluginMergeConfig)
      }

      override def bootstrapModules(): Seq[ModuleBase] = Seq(
        new ConfigModule(bsContext.appConfig)
        , new CustomizationModule
      )

      override def requiredComponents: Set[RuntimeDIUniverse.DIKey] = Set(
        RuntimeDIUniverse.DIKey.get[TestApp]
      )

      override def router(): LogRouter = {
        LoggingMacroTest.mkRouter(testSink)
      }
    }
  }

  override protected def start(context: Locator, bootstrapContext: Strategy#Context): Unit = {
    callback(context, bootstrapContext)
  }

  private val pluginMergeConfig = PluginMergeConfig(
    disabledImplementations = Set(classOf[DisabledImpl].getName, classOf[DisabledBinding].getName)
    , disabledTags = Set("badtag")
    , disabledKeys = Set(classOf[DisabledTrait].getName)
    , preferences = Map(classOf[Conflict].getSimpleName -> BindingPreference(Some("B"), None))
  )

  private val pluginConfig = PluginConfig(debug = false
    , Seq(classOf[TestApp].getPackage.getName)
    , Seq.empty
  )

  private val bootstrapConfig = {
    PluginConfig(debug = false
      , Seq("com.github.pshirshov.izumi")
      , Seq.empty
    )
  }
}


class OpinionatedDIAppTest extends WordSpec {

  "DI app" should {
    "support dynamic app loading" in {



      val app = new TestAppLauncher({
        case (context, bsContext) =>
          assert(context.find[TestApp].nonEmpty)
          assert(context.find[BadApp].isEmpty)
          assert(context.find[DisabledTrait].isEmpty)
          assert(context.find[DisabledBinding].isEmpty)
          assert(context.find[Conflict].exists(_.isInstanceOf[ConflictB]))

          assert(context.get[AppConfig] == bsContext.appConfig)
          assert(context.get[TestApp].config.value == "test")
          assert(context.get[TestApp].setTest.size == 1)

          ()
      })

      app.main(Array.empty)
    }
  }

}

