package izumi.distage.roles.launcher

import distage.Id
import distage.config.AppConfig
import logstage.{ConfigurableLogRouter, IzLogger}

import java.util.concurrent.ConcurrentHashMap

class LateLoggerFactoryCachingImpl(
  config: AppConfig,
  cliOptions: CLILoggerOptions,
  earlyLogger: IzLogger @Id("early"),
  cache: LateLoggerFactoryCachingImpl.Cache,
) extends LateLoggerFactory.LateLoggerFactoryImpl(config, cliOptions, earlyLogger) {
  override protected def createRouter(config: LateLoggerFactory.DeclarativeLoggerConfig): ConfigurableLogRouter = {
    cache.cache.computeIfAbsent(config, instantiateRouter)
  }

}

object LateLoggerFactoryCachingImpl {
  def makeCache(): Cache = {
    Cache(new ConcurrentHashMap[LateLoggerFactory.DeclarativeLoggerConfig, ConfigurableLogRouter])
  }

  case class Cache(cache: ConcurrentHashMap[LateLoggerFactory.DeclarativeLoggerConfig, ConfigurableLogRouter]) extends AutoCloseable {
    override def close(): Unit = {
      import scala.jdk.CollectionConverters.*
      cache.asScala.foreach(println)
      cache.asScala.values.foreach(_.close())
      cache.clear()
    }
  }
}
