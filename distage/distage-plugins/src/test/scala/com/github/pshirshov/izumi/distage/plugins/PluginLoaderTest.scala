package com.github.pshirshov.izumi.distage.plugins

import com.github.pshirshov.izumi.distage.Injectors
import com.github.pshirshov.izumi.distage.model.definition.TrivialModuleDef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.{Locator, reflection}
import com.github.pshirshov.izumi.logstage.api.Log.CustomContext
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import com.github.pshirshov.izumi.logstage.api.routing.LoggingMacroTest
import com.github.pshirshov.izumi.logstage.api.{IzLogger, TestSink}
import com.github.pshirshov.test.app.{BadApp, TestApp}
import org.scalatest.WordSpec


trait AppFailureHandler {
  def onError(t: Throwable): Unit
}


object TerminatingHandler extends AppFailureHandler {
  override def onError(t: Throwable): Unit = {
    t.printStackTrace()
    System.exit(1)
  }
}

object PrintingHandler extends AppFailureHandler {
  override def onError(t: Throwable): Unit = {
    t.printStackTrace()
    throw t
  }
}

object NullHandler extends AppFailureHandler {
  override def onError(t: Throwable): Unit = {
    throw t
  }
}



abstract class OpinionatedDiApp {
  def main(args: Array[String]): Unit = {
    try {
      doMain()
    } catch {
      case t: Throwable =>
        handler.onError(t)
    }
  }

  protected def start(context: Locator): Unit

  protected def handler: AppFailureHandler

  protected def bootstrapConfig: PluginConfig

  protected val appConfig: PluginConfig

  protected def router: LogRouter

  protected def mergeStrategy: PluginMergeStrategy[LoadedPlugins]

  protected def requiredComponents: Set[RuntimeDIUniverse.DIKey]

  protected def gc: DIGarbageCollector

  protected def doMain(): Unit = {
    val logger = new IzLogger(router, CustomContext.empty) // TODO: add instance/machine id here?
    val bootstrapLoader = new PluginLoader(bootstrapConfig)
    val appLoader = new PluginLoader(appConfig)

    val bootstrapAutoDef = bootstrapLoader.loadDefinition(mergeStrategy)
    val bootstrapCustomDef = TrivialModuleDef.bind[LogRouter](router)
    val appDef = appLoader.loadDefinition(mergeStrategy)
    logger.trace(s"Have bootstrap definition\n$appDef")
    logger.trace(s"Have app definition\n$appDef")

    val injector = Injectors.bootstrap(bootstrapAutoDef.definition ++ bootstrapCustomDef)
    val plan = injector.plan(appDef.definition)
    logger.trace(s"Planning completed\n$plan")
    val refinedPlan = gc.gc(plan, requiredComponents)
    logger.trace(s"Unrequired components disabled\n$refinedPlan")
    val context = injector.produce(refinedPlan)
    logger.trace(s"Context produced")
    start(context)
  }
}


class PluginLoaderTest extends WordSpec {

  "Plugin loader" should {
    "support be able to load apps dynamically" in {
      val testSink = new TestSink()


      val app = new OpinionatedDiApp {
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

        override protected def start(context: Locator): Unit = {
          assert(context.find[TestApp].nonEmpty)
          assert(context.find[BadApp].isEmpty)

          ()
        }


        override protected def requiredComponents: Set[reflection.universe.RuntimeDIUniverse.DIKey] = Set(
          RuntimeDIUniverse.DIKey.get[TestApp]
        )

        override protected def gc: DIGarbageCollector = NullDiGC

        override protected def mergeStrategy: PluginMergeStrategy[LoadedPlugins] = SimplePluginMergeStrategy

        override protected def handler: AppFailureHandler = NullHandler
      }

      app.main(Array.empty)
    }
  }

}

