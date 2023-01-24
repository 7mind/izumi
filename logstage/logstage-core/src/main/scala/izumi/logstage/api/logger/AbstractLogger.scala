package izumi.logstage.api.logger

import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log
import izumi.logstage.api.Log.{CustomContext, LoggerId}

trait AbstractLoggerF[F[_]] {

  type Self <: AbstractLoggerF[F]

  /** Log irrespective of the log level threshold */
  def unsafeLog(entry: Log.Entry): F[Unit]

  /** Check if `loggerId` is not blacklisted and `logLevel` is above the configured threshold */
  def acceptable(loggerId: LoggerId, logLevel: Log.Level): F[Boolean]

  def withCustomContext(context: CustomContext): Self

  @inline def ifAcceptable(loggerId: LoggerId, logLevel: Log.Level)(action: => F[Unit])(implicit pos: CodePositionMaterializer): F[Unit]

  @inline final def apply(context: CustomContext): Self = withCustomContext(context)

  @inline final def acceptable(logLevel: Log.Level)(implicit pos: CodePositionMaterializer): F[Boolean] = {
    acceptable(LoggerId(pos.get.applicationPointId), logLevel)
  }

  /** Log Entry if `logLevel` is above the threshold configured for this logger. */
  @inline final def log(entry: Log.Entry): F[Unit] = {
    ifAcceptable(entry.context.static.id, entry.context.dynamic.level)(unsafeLog(entry))
  }

  /**
    * Construct Entry and log if `logLevel` is above the threshold configured for this logger.
    *
    * Does not allocate Entry if `logLevel` is below the requirement
    */
  @inline final def log(logLevel: Log.Level)(messageThunk: => Log.Message)(implicit pos: CodePositionMaterializer): F[Unit] = {
    ifAcceptable(LoggerId(pos.get.applicationPointId), logLevel)(unsafeLog(Log.Entry.create(logLevel, messageThunk)(pos)))
  }

}

trait AbstractLogger extends AbstractLoggerF[Identity] {
  type Self <: AbstractLogger

  def unsafeLog(entry: Log.Entry): Unit

  def acceptable(loggerId: LoggerId, logLevel: Log.Level): Boolean

  @inline final def ifAcceptable(loggerId: LoggerId, logLevel: Log.Level)(action: => Unit)(implicit pos: CodePositionMaterializer): Unit = {
    if (acceptable(loggerId, logLevel)) {
      action
    }
  }

}
