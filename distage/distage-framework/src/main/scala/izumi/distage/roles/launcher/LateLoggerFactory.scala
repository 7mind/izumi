package izumi.distage.roles.launcher

import com.typesafe.config.Config
import distage.Id
import distage.config.{AppConfig, DIConfigReader}
import izumi.logstage.adapter.jul.LogstageJulLogger
import izumi.logstage.api.Log
import izumi.logstage.api.Log.Level.Warn
import izumi.logstage.api.Log.Message
import izumi.logstage.api.config.{LoggerConfig, LoggerPathConfig}
import izumi.logstage.api.logger.LogRouter
import izumi.logstage.api.rendering.{RenderingOptions, StringRenderingPolicy}
import izumi.logstage.api.routing.{LogConfigServiceImpl, StaticLogRouter}
import logstage.circe.LogstageCirceRenderingPolicy
import logstage.{ConfigurableLogRouter, ConsoleSink, IzLogger, QueueingSink}

import scala.util.Try

trait LateLoggerFactory {
  def makeLateLogRouter(): LogRouter
}

object LateLoggerFactory {
  sealed trait LoggerFormat

  object LoggerFormat {
    final case object Json extends LoggerFormat

    final case object Text extends LoggerFormat
  }

  case class DeclarativeLoggerConfig(
    format: LoggerFormat,
    rendering: RenderingOptions,
    levels: Map[String, Log.Level],
    rootLevel: Log.Level,
  )

  final case class SinksConfig(
    levels: Map[String, List[String]],
    options: Option[RenderingOptions],
    json: Option[Boolean],
    jul: Option[Boolean],
  )

  object SinksConfig {
    implicit val configReader: DIConfigReader[SinksConfig] = DIConfigReader.derived
  }

  class LateLoggerFactoryImpl(
    config: AppConfig,
    cliOptions: CLILoggerOptions,
    earlyLogger: IzLogger @Id("early"),
  ) extends LateLoggerFactory {
    final def makeLateLogRouter(): LogRouter = {
      val logconf = readConfig(config.config)
      val isJson = cliOptions.json || logconf.json.contains(true)
      val options = logconf.options.getOrElse(RenderingOptions.default)
      val jul = logconf.jul.getOrElse(true)

      val levels = logconf.levels.flatMap {
        case (stringLevel, packageList) =>
          val level = Log.Level.parseLetter(stringLevel)
          packageList.map(pkg => (pkg, level))
      }

      val format = if (isJson) {
        LoggerFormat.Json
      } else {
        LoggerFormat.Text
      }

      val fullConfig = DeclarativeLoggerConfig(format, options, levels, cliOptions.level)
      val router = createRouter(fullConfig)

      StaticLogRouter.instance.setup(router)

      if (jul) {
        val julAdapter = new LogstageJulLogger(router)
        julAdapter.installOnly()
      }

      router
    }

    protected def createRouter(config: DeclarativeLoggerConfig): ConfigurableLogRouter = {
      instantiateRouter(config)
    }

    protected final def instantiateRouter(config: DeclarativeLoggerConfig): ConfigurableLogRouter = {
      val policy = config.format match {
        case LoggerFormat.Json =>
          new LogstageCirceRenderingPolicy()
        case LoggerFormat.Text =>
          new StringRenderingPolicy(config.rendering, None)
      }

      val queueingSink = new QueueingSink(new ConsoleSink(policy))
      val sinks = Seq(queueingSink)
      val levelConfigs = config.levels.map {
        case (pkg, level) =>
          (pkg, LoggerPathConfig(level, sinks))
      }

      // TODO: here we may read log configuration from config file
      val router = new ConfigurableLogRouter(
        new LogConfigServiceImpl(
          LoggerConfig(LoggerPathConfig(config.rootLevel, sinks), levelConfigs)
        )
      )
      queueingSink.start()

      router
    }

    private[this] def readConfig(config: Config): SinksConfig = {
      Try(config.getConfig("logger")).toEither.left
        .map(_ => Message("No `logger` section in config. Using defaults."))
        .flatMap {
          config =>
            SinksConfig.configReader.decodeConfigValue(config.root).toEither.left.map {
              exception =>
                Message(s"Failed to parse `logger` config section into ${classOf[SinksConfig] -> "type"}. Using defaults. $exception")
            }
        } match {
        case Left(errMessage) =>
          earlyLogger.log(Warn)(errMessage)
          SinksConfig(Map.empty, None, None, None)

        case Right(value) =>
          value
      }
    }
  }
}
