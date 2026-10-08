package izumi.logstage.distage

import cats.effect.IO
import distage.{DefaultModule, Injector, Roots}
import izumi.functional.quasi.QuasiIORunner
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.api.routing.ConfigurableLogRouter
import izumi.logstage.api.{IzLogger, TestSink}
import logstage.{LogCreateIO, LogIO, UnsafeLogIO}
import izumi.distage.testkit.runner.spec.AnyWordSpec
import zio.Task

final class NativeLoggerInjectionTest extends AnyWordSpec {
  "Native effect logging modules" should {
    "inject and execute a suspended Cats IO logger" in {
      val sink = new TestSink()
      val router = ConfigurableLogRouter(IzLogger.Level.Trace, sink)
      val module = DefaultModule.forCatsIO.module ++ LogIOModule[IO](router, setupStaticLogRouter = false)

      Injector[Identity]().produce(module, Roots.Everything).use {
        objects =>
          val logger = objects.get[LogIO[IO]]
          assert(objects.get[UnsafeLogIO[IO]] eq logger)
          assert(objects.get[LogCreateIO[IO]] eq logger)
          val action = logger.info("Native injected Cats IO log")
          assert(sink.fetch().isEmpty)
          objects.get[QuasiIORunner[IO]].runBlocking(action)
      }

      assert(sink.fetch().map(_.message.template.parts) == Seq(Seq("Native injected Cats IO log")))
    }

    "inject and execute a suspended ZIO logger" in {
      val sink = new TestSink()
      val router = ConfigurableLogRouter(IzLogger.Level.Trace, sink)
      val defaults: DefaultModule[Task] = DefaultModule.forZIO
      val module = defaults.module ++ LogIOModule[Task](router, setupStaticLogRouter = false)

      Injector[Identity]().produce(module, Roots.Everything).use {
        objects =>
          val logger = objects.get[LogIO[Task]]
          assert(objects.get[UnsafeLogIO[Task]] eq logger)
          assert(objects.get[LogCreateIO[Task]] eq logger)
          val action = logger.info("Native injected ZIO log")
          assert(sink.fetch().isEmpty)
          objects.get[QuasiIORunner[Task]].runBlocking(action)
      }

      assert(sink.fetch().map(_.message.template.parts) == Seq(Seq("Native injected ZIO log")))
    }
  }
}
