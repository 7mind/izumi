package izumi.logstage.api.routing

import java.util.concurrent.atomic.AtomicReference

import izumi.logstage.api.Log
import izumi.logstage.api.logger.LogRouter
import izumi.logstage.sink.ConsoleSink

/**
  * When not configured, `logstage-adapter-slf4j` will log messages with level `>= Info` to `stdout`.
  *
  * Due to the global mutable nature of `slf4j` to configure slf4j logging you'll
  * have to mutate a global singleton. To change its settings, replace its `LogRouter`
  * with the same one you use elsewhere in your application.
  *
  * {{{
  *   import logstage._
  *   import izumi.logstage.api.routing.StaticLogRouter
  *
  *   val myLogger = IzLogger()
  *
  *   // configure SLF4j to use the same router that `myLogger` uses
  *   StaticLogRouter.instance.setup(myLogger.router)
  * }}}
  */
class StaticLogRouter extends LogRouter {

  private val proxied = new AtomicReference[LogRouter](null)

  private val fallbackSink = ConsoleSink.ColoredConsoleSink
  private val fallbackThreshold = Log.Level.Info

  def setup(router: LogRouter): Unit = {
    proxied.set(router)
  }

  def get(): LogRouter = proxied.get()

  override def acceptable(id: Log.LoggerId, messageLevel: Log.Level): Boolean = {
    proxied.get() match {
      case null =>
        messageLevel >= fallbackThreshold

      case p =>
        p.acceptable(id, messageLevel)
    }
  }

  override def log(entry: Log.Entry): Unit = {
    proxied.get() match {
      case null =>
        if (acceptable(entry.context.static.id, entry.context.dynamic.level))
          fallbackSink.flush(entry)

      case p =>
        p.log(entry)
    }
  }

  override def close(): Unit = {
    proxied.set(null)
  }
}

object StaticLogRouter {
  final val instance = new StaticLogRouter
}
