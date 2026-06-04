package izumi.distage.roles.test.fixtures

import izumi.distage.model.definition.{Module, ModuleDef}
import izumi.distage.plugins.PluginConfig
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.launcher.LoggerConfigLoader.DeclarativeLoggerConfig
import izumi.distage.roles.launcher.{AppFailureHandler, EarlyLoggerFactory, RouterFactory}
import izumi.logstage.api.logger.{LogQueue, LogSink}
import izumi.logstage.api.routing.ConfigurableLogRouter
import izumi.logstage.api.{IzLogger, Log}

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

// In-memory accumulating sink (cf. izumi.logstage.api.TestSink) — records entries so a fatal failure can be matched
// against recorded structured output instead of a global stdout capture.
final class CapturingFixture3Sink extends LogSink {
  private val entries = new ConcurrentLinkedQueue[Log.Entry]()
  override def flush(e: Log.Entry): Unit = { entries.add(e); () }
  def fetch(): Seq[Log.Entry] = entries.asScala.toSeq
}

// Fixture3 launcher that routes both the early and late loggers to the given sink via roleAppBootOverrides.
final class CapturingFixture3Main(sink: LogSink) extends RoleAppMain.LauncherIdentity {
  override protected def pluginConfig: PluginConfig = PluginConfig.cached("com.github.pshirshov.test3.plugins")
  override protected def bootstrapPluginConfig: PluginConfig = PluginConfig.cached("com.github.pshirshov.test3.bootstrap")
  override protected def earlyFailureHandler(args: RoleAppMain.ArgV): AppFailureHandler =
    new AppFailureHandler.TerminatingHandler(sysExit = _ => ())
  override protected def roleAppBootOverrides(argv: RoleAppMain.ArgV): Module = new ModuleDef {
    make[EarlyLoggerFactory].fromValue(new EarlyLoggerFactory {
      override def makeEarlyLogger(): IzLogger = IzLogger(sink = sink)
    })
    make[RouterFactory].fromValue(new RouterFactory {
      override def createRouter(config: DeclarativeLoggerConfig, buffer: LogQueue): ConfigurableLogRouter =
        ConfigurableLogRouter(config.rootLevel, Seq(sink), buffer)
    })
  }
}
