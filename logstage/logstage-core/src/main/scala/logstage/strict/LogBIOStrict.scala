package logstage.strict

import izumi.functional.bio.SyncSafe2
import izumi.logstage.api.logger.AbstractLogger

object LogBIOStrict {
  def apply[F[_, _]: LogBIOStrict]: LogBIOStrict[F] = implicitly

  def fromLogger[F[_, _]: SyncSafe2](logger: AbstractLogger): LogBIOStrict[F] = {
    LogIOStrict.fromLogger(logger)
  }

  /**
    * Lets you refer to an implicit logger's methods without naming a variable
    *
    * {{{
    *   import logstage.LogBIOStrict.log
    *
    *   def fn[F[_, _]: LogBIOStrict]: F[Nothing, Unit] = {
    *     log.info(s"I'm logging with ${log}stage!")
    *   }
    * }}}
    */
  @inline def log[F[_, _]](implicit l: LogBIOStrict[F]): l.type = l
}
