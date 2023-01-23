package izumi.distage.roles.launcher

import izumi.logstage.api.IzLogger
import izumi.logstage.sink.ConsoleSink
import logstage.circe.LogstageCirceRenderingPolicy

trait EarlyLoggerFactory {
  def makeEarlyLogger(): IzLogger
}

object EarlyLoggerFactory {
  class EarlyLoggerFactoryImpl(
    cliOptions: CLILoggerOptions
  ) extends EarlyLoggerFactory {
    override def makeEarlyLogger(): IzLogger = {
      val sink = if (cliOptions.json) {
        new ConsoleSink(new LogstageCirceRenderingPolicy())
      } else {
        ConsoleSink.ColoredConsoleSink
      }

      IzLogger(cliOptions.level, sink)("phase" -> "early")
    }
  }
}
