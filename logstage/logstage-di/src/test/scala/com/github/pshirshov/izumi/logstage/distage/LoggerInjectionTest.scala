package com.github.pshirshov.izumi.logstage.distage

import com.github.pshirshov.izumi.distage.Injector
import com.github.pshirshov.izumi.distage.model.definition.{ContextDefinition, TrivialDIDef}
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
  def mkInjector(): Injector = Injector.emerge()

  "Logging module for distage" should {
    "inject loggers" in {
      val router: LogRouter = LoggingMacroTest.mkRouter(LoggingMacroTest.consoleSinkText)

      val definition: ContextDefinition = TrivialDIDef
        .instance(router)
        .instance(CustomContext.empty)
        .binding[IzLogger]
        .binding[ExampleService]
        .binding[ExampleApp]

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)
      assert(context.get[ExampleApp].test == 265)
    }
  }
}
