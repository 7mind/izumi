package logstage

import izumi.functional.bio.SyncSafe2
import izumi.logstage.api.logger.AbstractLogger

object LogBIO {
  def apply[F[_, _]: LogBIO]: LogBIO[F] = implicitly

  def fromLogger[F[_, _]: SyncSafe2](logger: AbstractLogger): LogBIO[F] = {
    LogIO.fromLogger(logger)
  }

  /**
    * Lets you refer to an implicit logger's methods without naming a variable
    *
    * {{{
    *   import logstage.LogBIO.log
    *
    *   def fn[F[_, _]: LogBIO]: F[Nothing, Unit] = {
    *     log.info(s"I'm logging with ${log}stage!")
    *   }
    * }}}
    */
  @inline def log[F[_, _]](implicit l: LogBIO[F]): l.type = l
}
