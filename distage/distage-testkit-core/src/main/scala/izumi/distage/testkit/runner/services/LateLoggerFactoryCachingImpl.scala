package izumi.distage.testkit.runner.services

import distage.Id
import distage.config.AppConfig
import izumi.distage.roles.launcher.{CLILoggerOptions, LateLoggerFactory}
import logstage.{ConfigurableLogRouter, IzLogger}

import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedDeque}

/**
  * This is a dirty solution which allows us to share identical logger routers between multiple tests
  */
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
    Cache(new ConcurrentHashMap[LateLoggerFactory.DeclarativeLoggerConfig, ConfigurableLogRouter], new ConcurrentLinkedDeque[AutoCloseable])
  }

  case class Cache(
    cache: ConcurrentHashMap[LateLoggerFactory.DeclarativeLoggerConfig, ConfigurableLogRouter],
    closeables: ConcurrentLinkedDeque[AutoCloseable],
  ) extends AutoCloseable {
    override def close(): Unit = {
      import scala.jdk.CollectionConverters.*
      closeables.asScala.foreach(_.close())
      closeables.clear()
      cache.clear()
    }
  }
}
