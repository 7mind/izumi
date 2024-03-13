package izumi.logstage.distage

import izumi.distage.model.PlannerInput
import izumi.logstage.api.routing.ConfigurableLogRouter
import izumi.logstage.api.{IzLogger, TestSink}
import distage.{Injector, ModuleDef}
import org.scalatest.wordspec.AnyWordSpec

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

class LoggerInjectionTest extends AnyWordSpec {
  "Logging module for distage" should {
    "inject loggers" in {
      val testSink = new TestSink()
      val router = ConfigurableLogRouter(IzLogger.Level.Trace, testSink)

      val definition = PlannerInput.everything(new ModuleDef {
        make[ExampleService]
        make[ExampleApp]
      })

      val loggerModule = new LogstageModule(router, false)

      val injector = Injector(loggerModule)
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()
      assert(context.get[ExampleApp].test == 265)

      val messages = testSink.fetch()
      assert(messages.size >= 2)
      val last = messages.takeRight(2)
      assert(last.head.message.template.toString.contains("-App-"))
      assert(last.last.message.template.toString.contains("-Service-"))
    }
  }
}
