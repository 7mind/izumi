package com.github.pshirshov.izumi.distage.plugins

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.{Locator, reflection}
import com.github.pshirshov.izumi.logstage.api.TestSink
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import com.github.pshirshov.izumi.logstage.api.routing.LoggingMacroTest
import com.github.pshirshov.test.app.{BadApp, TestApp}
import org.scalatest.WordSpec


class PluginLoaderTest extends WordSpec {

  "Plugin loader" should {
    "support be able to load apps dynamically" in {


      val app = new OpinionatedDiApp {
        override protected def start(context: Locator): Unit = {
          assert(context.find[TestApp].nonEmpty)
          assert(context.find[BadApp].isEmpty)

          ()
        }

        val testSink = new TestSink()

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

        override protected def handler: AppFailureHandler = NullHandler
      }

      app.main(Array.empty)
    }
  }

}

