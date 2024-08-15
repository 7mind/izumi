package izumi.logstage.api.logger

import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.{CodePosition, CodePositionMaterializer}
import izumi.logstage.api.Log
import izumi.logstage.api.Log.{CustomContext, LoggerId}

trait AbstractLoggerF[F[_]] {

  type Self <: AbstractLoggerF[F]

  /** Log irrespective of the log level threshold */
  def unsafeLog(entry: Log.Entry): F[Unit]

  /** Check if `loggerId` is not blacklisted and `logLevel` is above the configured threshold */
  def acceptable(loggerId: LoggerId, logLevel: Log.Level): F[Boolean]

  def withCustomContext(context: CustomContext): Self

  @inline def ifAcceptable(loggerId: LoggerId, line: Int, logLevel: Log.Level)(action: => F[Unit]): F[Unit]

  @inline final def apply(context: CustomContext): Self = withCustomContext(context)

  @inline final def acceptable(logLevel: Log.Level)(implicit pos: CodePositionMaterializer): F[Boolean] = {
    acceptable(LoggerId(pos.get.applicationPointId), logLevel)
  }

  /** Log Entry if `logLevel` is above the threshold configured for this logger. */
  @inline final def log(entry: Log.Entry): F[Unit] = {
    ifAcceptable(entry.context.static.id, entry.context.static.position.line, entry.context.dynamic.level)(unsafeLog(entry))
  }

  /**
    * Construct Entry and log if `logLevel` is above the threshold configured for this logger.
    *
    * Does not allocate Entry if `logLevel` is below the requirement
    */
  @inline final def log(logLevel: Log.Level)(messageThunk: => Log.Message)(implicit pos: CodePositionMaterializer): F[Unit] = {
    ifAcceptable(LoggerId(pos.get.applicationPointId), pos.get.position.line, logLevel)(unsafeLog(Log.Entry.create(logLevel, messageThunk)(pos)))
  }

}

trait AbstractLogger extends AbstractLoggerF[Identity] {
  type Self <: AbstractLogger

  def unsafeLog(entry: Log.Entry): Unit

  def acceptable(loggerId: LoggerId, logLevel: Log.Level): Boolean
  def acceptable(loggerId: LoggerId, line: Int, logLevel: Log.Level): Boolean

  def acceptable(position: CodePosition, logLevel: Log.Level): Boolean

  @inline final def ifAcceptable(loggerId: LoggerId, line: Int, logLevel: Log.Level)(action: => Unit): Unit = {
    if (acceptable(loggerId, line, logLevel)) {
      action
    }
  }

}
