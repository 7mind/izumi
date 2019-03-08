package logstage

import com.github.pshirshov.izumi.functional.bio.SyncSafe2
import com.github.pshirshov.izumi.logstage.api.AbstractLogger

object LogBIO {
  def apply[F[_, _]: LogBIO]: LogBIO[F] = implicitly

  def fromLogger[F[_, _]: SyncSafe2](logger: AbstractLogger): LogBIO[F] = {
    LogIO.fromLogger(logger)
  }
}
