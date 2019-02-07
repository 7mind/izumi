package logstage

import com.github.pshirshov.izumi.functional.mono.SyncSafe
import com.github.pshirshov.izumi.logstage.api.AbstractLogger
import com.github.pshirshov.izumi.logstage.api.Log.{Entry, LoggerId}

trait UnsafeLogIO[+F[_]] {
  def unsafeLog(entry: Entry): F[Unit]
  def acceptable(loggerId: LoggerId, logLevel: Level): F[Boolean]
}

object UnsafeLogIO {
  def apply[F[_]: UnsafeLogIO]: UnsafeLogIO[F] = implicitly

  def fromLogger[F[_]: SyncSafe](logger: AbstractLogger): UnsafeLogIO[F] = {
    new UnsafeLogIO[F] {
      override def unsafeLog(entry: Entry): F[Unit] = {
        SyncSafe[F].syncSafe(logger.unsafeLog(entry))
      }

      override def acceptable(loggerId: LoggerId, logLevel: Level): F[Boolean] = {
        SyncSafe[F].syncSafe(logger.acceptable(loggerId, logLevel))
      }
    }
  }
}
