package izumi.distage.roles.launcher

import distage.{Id, Lifecycle}
import distage.config.{AppConfig, DIConfigReader}
import izumi.distage.roles.launcher.LateLoggerFactory.DistageAppLogging
import izumi.fundamentals.platform.functional.Identity
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

trait LateLoggerFactory  {
  def makeLateLogRouter(onClose: List[AutoCloseable] => Unit): Lifecycle[Identity, DistageAppLogging]

  def makeLateLogRouter(): Lifecycle[Identity, DistageAppLogging]
}

object LateLoggerFactory {
  sealed trait LoggerFormat

  object LoggerFormat {
    case object Json extends LoggerFormat
    case object Text extends LoggerFormat
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

  case class DistageAppLogging(
    router: LogRouter,
    closeables: List[AutoCloseable],
  )

  object SinksConfig {
    implicit val configReader: DIConfigReader[SinksConfig] = DIConfigReader.derived
  }

  class LateLoggerFactoryImpl(
    config: AppConfig,
    cliOptions: CLILoggerOptions,
    earlyLogger: IzLogger @Id("early"),
  ) extends LateLoggerFactory {
    final def makeLateLogRouter(): Lifecycle[Identity, DistageAppLogging] = makeLateLogRouter(_.foreach(_.close()))

    final def makeLateLogRouter(onClose: List[AutoCloseable] => Unit): Lifecycle[Identity, DistageAppLogging] = {
      val logconf = readConfig(config)
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

      Lifecycle.make[Identity, DistageAppLogging] {
        StaticLogRouter.instance.setup(router)
        if (jul) {
          val julAdapter = new LogstageJulLogger(router)
          julAdapter.installOnly()
          DistageAppLogging(router, List(julAdapter, router))
        } else {
          DistageAppLogging(router, List(router))
        }
      }(loggers => onClose(loggers.closeables))
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

    private[this] def readConfig(config: AppConfig): SinksConfig = {
      Try(config.config.getConfig("logger")).toEither.left
        .map(_ => Message("No `logger` section in config. Using defaults."))
        .flatMap {
          config =>
            SinksConfig.configReader.decodeConfig(config).toEither.left.map {
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
