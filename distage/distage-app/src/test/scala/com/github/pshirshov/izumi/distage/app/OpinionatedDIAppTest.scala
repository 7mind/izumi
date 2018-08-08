package com.github.pshirshov.izumi.distage.app

import com.github.pshirshov.izumi.distage.config.ConfigModule
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.definition.{ModuleBase, ModuleDef}
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.planning.AssignableFromEarlyAutoSetHook
import com.github.pshirshov.izumi.distage.planning.gc.TracingGcModule
import com.github.pshirshov.izumi.distage.plugins._
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl.PluginConfig
import com.github.pshirshov.izumi.distage.plugins.merge.ConfigurablePluginMergeStrategy.{BindingPreference, PluginMergeConfig}
import com.github.pshirshov.izumi.distage.plugins.merge.{ConfigurablePluginMergeStrategy, PluginMergeStrategy}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.tags.TagExpr
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import com.github.pshirshov.izumi.logstage.api.{IzLogger, TestSink}
import com.github.pshirshov.test.testapp._
import com.typesafe.config.ConfigFactory
import org.scalatest.WordSpec

case class EmptyCfg()

class CustomizationModule extends ModuleDef {
  many[PlanningHook]
    .add(new AssignableFromEarlyAutoSetHook[Conflict])
}

class TestAppLauncher(callback: (Locator, ApplicationBootstrapStrategy[EmptyCfg]#Context) => Unit) extends OpinionatedDiApp {
  override type CommandlineConfig = EmptyCfg

  override def handler: AppFailureHandler = NullHandler

  val testSink = new TestSink()

  override protected def commandlineSetup(args: Array[String]): Strategy = {
    val bsContext: BootstrapContext = BootstrapContextDefaultImpl(
      EmptyCfg()
      , bootstrapConfig
      , pluginConfig
      , AppConfig(ConfigFactory.load())
    )

    new ApplicationBootstrapStrategyBaseImpl(bsContext) {
      override def mergeStrategy(bs: Seq[PluginBase], app: Seq[PluginBase]): PluginMergeStrategy[LoadedPlugins] = {
        Quirks.discard(bs, app)
        new ConfigurablePluginMergeStrategy(pluginMergeConfig)
      }

      override def bootstrapModules(bs: LoadedPlugins, app: LoadedPlugins): Seq[ModuleBase] = {
        Quirks.discard(bs, app)
        Seq(
          new ConfigModule(bsContext.appConfig)
          , new CustomizationModule
          , new TracingGcModule(Set(
            RuntimeDIUniverse.DIKey.get[TestApp],
            RuntimeDIUniverse.DIKey.get[DisabledByKey],
            RuntimeDIUniverse.DIKey.get[DisabledByImpl],
            RuntimeDIUniverse.DIKey.get[DisabledByTag],
            RuntimeDIUniverse.DIKey.get[WithGoodTag],
            RuntimeDIUniverse.DIKey.get[Set[SetEl]],
            RuntimeDIUniverse.DIKey.get[WeakSetDep],
          ))
        )
      }

      override def router(): LogRouter = {
        IzLogger.basicRouter(IzLogger.Level.Trace, testSink)
      }
    }
  }

  override protected def start(context: Locator, bootstrapContext: Strategy#Context): Unit = {
    callback(context, bootstrapContext)
  }

  private val pluginMergeConfig = PluginMergeConfig(
    disabledTags = TagExpr.Strings.any("badtag")
    , disabledKeyClassnames = Set(
      classOf[DisabledByKey].getName
    )
    , disabledImplClassnames = Set(
      classOf[DisabledImplForByImplTrait].getName
    )
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
          assert(context.find[DisabledByGc].isEmpty)

          assert(context.find[WithGoodTag].nonEmpty)

          assert(context.find[DisabledByTag].isEmpty)
          assert(context.find[DisabledByImpl].isEmpty)
          assert(context.find[DisabledByKey].isEmpty)

          assert(context.find[Conflict].exists(_.isInstanceOf[ConflictB]))

          assert(context.get[AppConfig] == bsContext.appConfig)

          assert(context.get[TestApp].config.value == "test")
          assert(context.get[TestApp].setTest.size == 1)

          val dep = context.get[WeakSetDep]
          assert(dep.s1.size == 2)
          assert(dep.s1.contains(dep.g1))
          assert(dep.s1.exists(_.isInstanceOf[WeakSetStrong]))
          assert(!dep.s1.exists(_.isInstanceOf[WeakSetBad]))

          ()
      })

      app.main(Array.empty)
    }
  }

}

