package logstage

import izumi.functional.bio.SyncSafe2
import izumi.logstage.api.AbstractLogger

object LogBIO {
  def apply[F[_, _]: LogBIO]: LogBIO[F] = implicitly

  def fromLogger[F[_, _]: SyncSafe2](logger: AbstractLogger): LogBIO[F] = {
    LogIO.fromLogger(logger)
  }
}
