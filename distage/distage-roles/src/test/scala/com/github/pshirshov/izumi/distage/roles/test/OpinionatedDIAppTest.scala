//package com.github.pshirshov.izumi.distage.app
//
//import com.github.pshirshov.izumi.distage.roles.services.AppFailureHandler
//import com.github.pshirshov.izumi.distage.config.ConfigModule
//import com.github.pshirshov.izumi.distage.config.model.AppConfig
//import com.github.pshirshov.izumi.distage.model.Locator
//import com.github.pshirshov.izumi.distage.model.definition.{BindingTag, BootstrapModuleDef}
//import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
//import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
//import com.github.pshirshov.izumi.distage.planning.AssignableFromEarlyAutoSetHook
//import com.github.pshirshov.izumi.distage.plugins._
//import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl.PluginConfig
//import com.github.pshirshov.izumi.distage.plugins.merge.ConfigurablePluginMergeStrategy.{BindingPreference, PluginMergeConfig}
//import com.github.pshirshov.izumi.distage.plugins.merge.{ConfigurablePluginMergeStrategy, PluginMergeStrategy}
//import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
//import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
//import com.github.pshirshov.izumi.logstage.api.routing.ConfigurableLogRouter
//import com.github.pshirshov.izumi.logstage.api.{IzLogger, TestSink}
//import com.github.pshirshov.test.testapp._
//import com.typesafe.config.ConfigFactory
//import distage.{DIKey, Module}
//import org.scalatest.WordSpec
//
//case class EmptyCfg()
//
//class CustomizationModule extends BootstrapModuleDef {
//  many[PlanningHook]
//    .add(new AssignableFromEarlyAutoSetHook[Conflict])
//}
//
//class TestAppLauncher(callback: (TestAppLauncher, Locator) => Unit) extends OpinionatedDiApp {
//  override type CommandlineConfig = EmptyCfg
//
//  override def handler: AppFailureHandler = AppFailureHandler.NullHandler
//
//  val testSink = new TestSink()
//
//  val config = AppConfig(ConfigFactory.load())
//
//  override protected def commandlineSetup(args: Array[String]): EmptyCfg = EmptyCfg()
//
//  override protected def makeStrategy(cliConfig: EmptyCfg): ApplicationBootstrapStrategy = {
//    Quirks.discard(cliConfig)
//
//    new ApplicationBootstrapStrategy {
//      override val context: BootstrapConfig = BootstrapConfig(pluginConfig)
//
//      override def mergeStrategy(bs: Seq[PluginBase], app: Seq[PluginBase]): PluginMergeStrategy = {
//        Quirks.discard(bs, app)
//        new ConfigurablePluginMergeStrategy(pluginMergeConfig)
//      }
//
//      override def bootstrapModules(bs: MergedPlugins, app: MergedPlugins): Seq[BootstrapModuleDef] = {
//        Quirks.discard(bs, app)
//        Seq(
//          new ConfigModule(config)
//          , new CustomizationModule
//          //          , new GraphDumpBootstrapModule()
//        )
//      }
//
//      override def appModules(bs: MergedPlugins, app: MergedPlugins): Seq[Module] = {
//        Quirks.discard(bs, app)
//        Seq.empty
//      }
//
//      override def gcRoots(bs: MergedPlugins, app: MergedPlugins): Set[DIKey] = {
//        Quirks.discard(bs, app)
//        Set(
//          RuntimeDIUniverse.DIKey.get[TestApp],
//          RuntimeDIUniverse.DIKey.get[DisabledByKey],
//          RuntimeDIUniverse.DIKey.get[DisabledByImpl],
//          RuntimeDIUniverse.DIKey.get[DisabledByTag],
//          RuntimeDIUniverse.DIKey.get[WithGoodTag],
//          RuntimeDIUniverse.DIKey.get[Set[SetEl]],
//          RuntimeDIUniverse.DIKey.get[WeakSetDep],
//        )
//      }
//
//      override def router(): LogRouter = {
//        ConfigurableLogRouter(IzLogger.Level.Trace, testSink)
//      }
//    }
//  }
//
//  override protected def start(context: Locator): Unit = {
//    callback(this, context)
//  }
//
//  private val pluginMergeConfig = PluginMergeConfig(
//    disabledTags = BindingTag.Expressions.any(BindingTag("badtag"))
//    , disabledKeyClassnames = Set(
//      classOf[DisabledByKey].getName
//    )
//    , disabledImplClassnames = Set(
//      classOf[DisabledImplForByImplTrait].getName
//    )
//    , preferences = Map(classOf[Conflict].getSimpleName -> BindingPreference(Some("B"), None))
//  )
//
//  private val pluginConfig = PluginConfig(debug = false
//    , Seq(classOf[TestApp].getPackage.getName)
//    , Seq.empty
//  )
//}
//
//
//class OpinionatedDIAppTest extends WordSpec {
//
//  "DI app" should {
//    "support dynamic app loading" in {
//
//      val app = new TestAppLauncher({
//        case (launcher, context) =>
//          assert(context.find[TestApp].nonEmpty)
//          assert(context.find[BadApp].isEmpty)
//          assert(context.find[DisabledByGc].isEmpty)
//
//          assert(context.find[WithGoodTag].nonEmpty)
//
//          assert(context.find[DisabledByTag].isEmpty)
//          assert(context.find[DisabledByImpl].isEmpty)
//          assert(context.find[DisabledByKey].isEmpty)
//
//          assert(context.find[Conflict].exists(_.isInstanceOf[ConflictB]))
//
//          assert(context.get[AppConfig] == launcher.config)
//
//          assert(context.get[TestApp].config.value == "test")
//          assert(context.get[TestApp].setTest.size == 1)
//
//          val dep = context.get[WeakSetDep]
//          assert(dep.s1.size == 2)
//          assert(dep.s1.contains(dep.g1))
//          assert(dep.s1.exists(_.isInstanceOf[WeakSetStrong]))
//          assert(!dep.s1.exists(_.isInstanceOf[WeakSetBad]))
//
//          ()
//      })
//
//      app.main(Array.empty)
//    }
//  }
//
//}
//
