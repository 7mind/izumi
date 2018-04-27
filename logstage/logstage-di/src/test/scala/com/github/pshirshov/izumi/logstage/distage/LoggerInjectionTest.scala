package com.github.pshirshov.izumi.logstage.distage

import com.github.pshirshov.izumi.distage.Injectors
import com.github.pshirshov.izumi.distage.model.definition.TrivialDIDef
import com.github.pshirshov.izumi.distage.model.planning.PlanningObserver
import com.github.pshirshov.izumi.logstage.api.{IzLogger, LoggingMacroTest}
import com.github.pshirshov.izumi.logstage.model.Log.CustomContext
import com.github.pshirshov.izumi.logstage.model.logger.LogRouter
import org.scalatest.WordSpec

class ExampleService(log: IzLogger) {
  def compute: Int = {
    log.debug("Service")
    265
  }
}

class ExampleApp(log: IzLogger, service: ExampleService) {
  def test: Int = {
    log.debug("App")
    service.compute
  }
}


class LoggerInjectionTest extends WordSpec {
  "Logging module for distage" should {
    "inject loggers" in {
      val router = LoggingMacroTest.mkRouter(LoggingMacroTest.consoleSinkText)

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
    }
  }
}
