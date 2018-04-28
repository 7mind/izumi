package com.github.pshirshov.izumi.logstage.distage

import com.github.pshirshov.izumi.distage.Injectors
import com.github.pshirshov.izumi.distage.model.definition.TrivialDIDef
import com.github.pshirshov.izumi.distage.model.planning.PlanningObserver
import com.github.pshirshov.izumi.logstage.TestSink
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.Log.CustomContext
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import com.github.pshirshov.izumi.logstage.api.routing.LoggingMacroTest
import org.scalatest.WordSpec

class ExampleService(log: IzLogger) {
  def compute: Int = {
    log.debug("-Service-")
    265
  }
}

class ExampleApp(log: IzLogger, service: ExampleService) {
  def test: Int = {
    log.debug("-App-")
    service.compute
  }
}


class LoggerInjectionTest extends WordSpec {
  "Logging module for distage" should {
    "inject loggers" in {
      val testSink = new TestSink()
      val router = LoggingMacroTest.mkRouter(testSink)

      val definition = TrivialDIDef
        .binding[ExampleService]
        .binding[ExampleApp]


      val customizations = TrivialDIDef
        .instance[LogRouter](router)
        .instance(CustomContext.empty)
        .binding[IzLogger]
        .binding[PlanningObserver, PlanningObserverLoggingImpl]

      val injector = Injectors.bootstrap(customizations)
      val plan = injector.plan(definition)
      val context = injector.produce(plan)
      assert(context.get[ExampleApp].test == 265)

      val messages = testSink.fetch()
      assert(messages.size > 2)
      val last = messages.takeRight(2)
      assert(last.head.message.template.toString.contains("-App-"))
      assert(last.last.message.template.toString.contains("-Service-"))

    }
  }
}
