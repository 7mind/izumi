package logstage

import izumi.functional.bio.SyncSafe3
import izumi.logstage.api.logger.AbstractLogger

object LogBIO3 {
  def apply[F[_, _, _]: LogBIO3]: LogBIO3[F] = implicitly

  def fromLogger[F[_, _, _]: SyncSafe3](logger: AbstractLogger): LogBIO3[F] = {
    LogIO.fromLogger(logger)
  }

  /**
    * Lets you refer to an implicit logger's methods without naming a variable
    *
    * {{{
    *   import logstage.LogBIO3.log
    *
    *   def fn[F[_, _, _]: LogBIO3]: F[Any, Nothing, Unit] = {
    *     log.info(s"I'm logging with ${log}stage!")
    *   }
    * }}}
    */
  @inline def log[F[_, _, _]](implicit l: LogBIO3[F]): l.type = l
}
