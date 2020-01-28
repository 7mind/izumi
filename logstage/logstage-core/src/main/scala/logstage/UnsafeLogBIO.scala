package logstage

import izumi.functional.bio.SyncSafe2
import izumi.logstage.api.logger.AbstractLogger

object UnsafeLogBIO {
  def apply[F[_, _]: UnsafeLogBIO]: UnsafeLogBIO[F] = implicitly

  def fromLogger[F[_, _]: SyncSafe2](logger: AbstractLogger): UnsafeLogBIO[F] = {
    UnsafeLogIO.fromLogger(logger)
  }
}
