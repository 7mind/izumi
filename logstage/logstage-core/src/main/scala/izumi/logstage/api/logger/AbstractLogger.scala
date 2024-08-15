package izumi.logstage.api.logger

import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.{CodePosition, CodePositionMaterializer}
import izumi.logstage.api.Log
import izumi.logstage.api.Log.CustomContext

trait AbstractLoggerF[F[_]] {

  type Self <: AbstractLoggerF[F]

  def withCustomContext(context: CustomContext): Self

  @inline final def apply(context: CustomContext): Self = withCustomContext(context)

  /** Check if `loggerId` is not blacklisted and `logLevel` is above the configured threshold */
  def acceptable(position: CodePosition, logLevel: Log.Level): F[Boolean]

  /** Log irrespective of the log level threshold */
  def unsafeLog(entry: Log.Entry): F[Unit]

  /** Log Entry if `logLevel` is above the threshold configured for this logger. */
  @inline final def log(entry: Log.Entry): F[Unit] = {
    ifAcceptable(entry.context.static.pos, entry.context.dynamic.level)(unsafeLog(entry))
  }

  /**
    * Construct Entry and log if `logLevel` is above the threshold configured for this logger.
    *
    * Does not allocate Entry if `logLevel` is below the requirement
    */
  @inline final def log(logLevel: Log.Level)(messageThunk: => Log.Message)(implicit pos: CodePositionMaterializer): F[Unit] = {
    ifAcceptable(pos.get, logLevel)(unsafeLog(Log.Entry.create(logLevel, messageThunk)(pos)))
  }

  @inline protected def ifAcceptable(position: CodePosition, logLevel: Log.Level)(action: => F[Unit]): F[Unit]
}

trait AbstractLogger extends AbstractLoggerF[Identity] {
  type Self <: AbstractLogger

  def unsafeLog(entry: Log.Entry): Unit

  def acceptable(position: CodePosition, logLevel: Log.Level): Boolean

  @inline protected final def ifAcceptable(position: CodePosition, logLevel: Log.Level)(action: => Unit): Unit = {
    if (acceptable(position, logLevel)) {
      action
    }
  }

}
