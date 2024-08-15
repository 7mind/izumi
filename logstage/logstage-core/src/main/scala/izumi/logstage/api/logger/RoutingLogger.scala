package izumi.logstage.api.logger

import izumi.fundamentals.platform.language.CodePosition
import izumi.logstage.api.Log.{CustomContext, LoggerId}
import izumi.logstage.api.Log

/** Logger that forwards entries to [[LogRouter]] */
trait RoutingLogger extends AbstractLogger {

  override type Self <: RoutingLogger

  def router: LogRouter
  def customContext: CustomContext

  @inline override final def acceptable(loggerId: Log.LoggerId, logLevel: Log.Level): Boolean = {
    router.acceptable(loggerId, logLevel)
  }

  override def acceptable(loggerId: Log.LoggerId, line: Int, logLevel: Log.Level): Boolean = {
    router.acceptable(loggerId, line, logLevel)
  }

  override def acceptable(position: CodePosition, logLevel: Log.Level): Boolean =
    router.acceptable(LoggerId(position.applicationPointId), position.position.line, logLevel)

  /** Log irrespective of minimum log level */
  @inline override final def unsafeLog(entry: Log.Entry): Unit = {
    val entryWithCtx = entry.addCustomContext(customContext)
    router.log(entryWithCtx)
  }

}
