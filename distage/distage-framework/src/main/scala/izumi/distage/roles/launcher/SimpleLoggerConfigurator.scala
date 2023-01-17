//package izumi.distage.roles.launcher
//
//import com.typesafe.config.Config
//import izumi.distage.config.codec.DIConfigReader
//import izumi.distage.roles.launcher.SimpleLoggerConfigurator.SinksConfig
//import izumi.logstage.api.Log.Level.Warn
//import izumi.logstage.api.Log.Message
//import izumi.logstage.api.config.{LoggerConfig, LoggerPathConfig}
//import izumi.logstage.api.logger.LogRouter
//import izumi.logstage.api.rendering.json.LogstageCirceRenderingPolicy
//import izumi.logstage.api.rendering.{RenderingOptions, StringRenderingPolicy}
//import izumi.logstage.api.routing.{ConfigurableLogRouter, LogConfigServiceImpl, StaticLogRouter}
//import izumi.logstage.api.{IzLogger, Log}
//import izumi.logstage.sink.{ConsoleSink, QueueingSink}
//
//import java.lang.ref.SoftReference
//import java.util.concurrent.ConcurrentHashMap
//import scala.util.Try
//
//
//class SimpleLoggerConfigurator(
//  exceptionLogger: IzLogger
//) {
//
//  // TODO: this is a temporary solution until we finish full-scale logger configuration support
//  def makeLogRouter(config: Config, root: Log.Level, json: Boolean): LogRouter = {
//    val logconf = readConfig(config)
//
//    val isJson = logconf.json.contains(true) || json
//    val options = logconf.options.getOrElse(RenderingOptions.default)
//
//    // loggers are difficult to memoize due to the very special way we need to initialize them
//    // this is a very dirty workaround and a better solution needs to be found. 
//    // Probably we need to rewrite whole test runner to make things better
//    SimpleLoggerConfigurator.routerCache
//      .compute(
//        SimpleLoggerConfigurator.RouterCacheKey(isJson, options),
//        (key, value) => {
//
//          if (value == null || value.get() == null) {
//            val renderingPolicy = if (key.isJson) {
//              new LogstageCirceRenderingPolicy()
//            } else {
//              new StringRenderingPolicy(key.options, None)
//            }
//
//            val queueingSink = new QueueingSink(new ConsoleSink(renderingPolicy))
//            val sinks = Seq(queueingSink)
//
//            val levels = logconf.levels.flatMap {
//              case (stringLevel, pack) =>
//                val level = Log.Level.parseLetter(stringLevel)
//                pack.map(_ -> LoggerPathConfig(level, sinks))
//            }
//
//            // TODO: here we may read log configuration from config file
//            val result = new ConfigurableLogRouter(
//              new LogConfigServiceImpl(
//                LoggerConfig(LoggerPathConfig(root, sinks), levels)
//              )
//            )
//            queueingSink.start()
//            StaticLogRouter.instance.setup(result)
//            new SoftReference[LogRouter](result)
//          } else {
//            value
//          }
//        },
//      ).get()
//
//  }
//
//  private[this] def readConfig(config: Config): SinksConfig = {
//    Try(config.getConfig("logger")).toEither.left
//      .map(_ => Message("No `logger` section in config. Using defaults."))
//      .flatMap {
//        config =>
//          SinksConfig.configReader.decodeConfigValue(config.root).toEither.left.map {
//            exception =>
//              Message(s"Failed to parse `logger` config section into ${classOf[SinksConfig] -> "type"}. Using defaults. $exception")
//          }
//      } match {
//      case Left(errMessage) =>
//        exceptionLogger.log(Warn)(errMessage)
//        SinksConfig(Map.empty, None, None)
//
//      case Right(value) =>
//        value
//    }
//  }
//}
//
//object SimpleLoggerConfigurator {
//  final case class SinksConfig(
//    levels: Map[String, List[String]],
//    options: Option[RenderingOptions],
//    json: Option[Boolean],
//  )
//  object SinksConfig {
//    implicit val configReader: DIConfigReader[SinksConfig] = DIConfigReader.derived
//  }
//
//  case class RouterCacheKey(isJson: Boolean, options: RenderingOptions)
//
//  val routerCache = new ConcurrentHashMap[RouterCacheKey, SoftReference[LogRouter]]()
//}
