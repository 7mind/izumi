package logstage

import izumi.functional.bio.SyncSafe2
import izumi.logstage.api.logger.AbstractLogger

object LogBIOStrict {
  def apply[F[_, _]: LogBIOStrict]: LogBIOStrict[F] = implicitly

  def fromLogger[F[_, _]: SyncSafe2](logger: AbstractLogger): LogBIOStrict[F] = {
    LogIOStrict.fromLogger(logger)
  }
}
