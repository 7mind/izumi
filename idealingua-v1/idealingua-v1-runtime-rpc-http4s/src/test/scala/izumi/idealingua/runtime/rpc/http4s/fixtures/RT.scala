package izumi.idealingua.runtime.rpc.http4s.fixtures

import java.util.concurrent.{Executors, ThreadPoolExecutor}

import izumi.functional.bio
import izumi.functional.bio.BIORunner
import izumi.idealingua.runtime.rpc.http4s.Http4sRuntime
import izumi.logstage.api.routing.{ConfigurableLogRouter, StaticLogRouter}
import izumi.logstage.api.{IzLogger, Log}
import io.circe.Printer
import zio.Runtime
import zio.clock.Clock
import zio.internal.tracing.TracingConfig
import zio.interop.catz._
import zio.interop.catz.implicits._

import scala.concurrent.ExecutionContext.global

object RT {
  final val logger = makeLogger()
  final val printer: Printer = Printer.noSpaces.copy(dropNullValues = true)
  implicit val clock: Clock = Clock.Live

  final val handler = BIORunner.FailureHandler.Custom(message => logger.warn(s"Fiber failed: $message"))
  val platform = new bio.BIORunner.ZIOPlatform(
    Executors.newFixedThreadPool(8).asInstanceOf[ThreadPoolExecutor]
  , handler
  , 1024
  , TracingConfig.enabled
  )
  implicit val runtime: Runtime[Any] = Runtime((), platform)
  implicit val BIOR: BIORunner[zio.IO] = BIORunner.createZIO(platform)
  final val rt = new Http4sRuntime[zio.IO, DummyRequestContext, DummyRequestContext, String, Unit, Unit](global)

  private def makeLogger(): IzLogger = {
    val router = ConfigurableLogRouter(Log.Level.Info, levels = Map(
      "org.http4s" -> Log.Level.Warn
      , "org.http4s.server.blaze" -> Log.Level.Error
      , "org.http4s.blaze.channel.nio1" -> Log.Level.Crit
      , "izumi.idealingua.runtime.rpc.http4s" -> Log.Level.Crit
    ))

    val out = IzLogger(router)
    StaticLogRouter.instance.setup(router)
    out
  }

}
