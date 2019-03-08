package logstage

import com.github.pshirshov.izumi.functional.bio.SyncSafe2
import com.github.pshirshov.izumi.logstage.api.AbstractLogger

object UnsafeLogBIO {
  def apply[F[_, _]: UnsafeLogBIO]: UnsafeLogBIO[F] = implicitly

  def fromLogger[F[_, _]: SyncSafe2](logger: AbstractLogger): UnsafeLogBIO[F] = {
    UnsafeLogIO.fromLogger(logger)
  }
}
