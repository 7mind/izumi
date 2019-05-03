package com.github.pshirshov.izumi.logstage.distage

import com.github.pshirshov.izumi.distage.model.PlannerInput
import com.github.pshirshov.izumi.logstage.api.routing.ConfigurableLogRouter
import com.github.pshirshov.izumi.logstage.api.{IzLogger, TestSink}
import distage.{Injector, ModuleDef}
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
      val router = ConfigurableLogRouter(IzLogger.Level.Trace, testSink)

      val definition = PlannerInput.noGc(new ModuleDef {
        make[ExampleService]
        make[ExampleApp]
      })

      val loggerModule = new LogstageModule(router, false)

      val injector = Injector.Standard(loggerModule)
      val plan = injector.plan(definition)
      val context = injector.produceUnsafe(plan)
      assert(context.get[ExampleApp].test == 265)

      val messages = testSink.fetch()
      assert(messages.size > 2)
      val last = messages.takeRight(2)
      assert(last.head.message.template.toString.contains("-App-"))
      assert(last.last.message.template.toString.contains("-Service-"))
    }
  }
}
