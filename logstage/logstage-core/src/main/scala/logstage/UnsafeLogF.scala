package logstage

import com.github.pshirshov.izumi.functional.mono.SyncSafe
import com.github.pshirshov.izumi.logstage.api.Log.{Entry, LoggerId}

trait UnsafeLogF[+F[_]] {
  def unsafeLog(entry: Entry): F[Unit]
  def acceptable(loggerId: LoggerId, logLevel: Level): F[Boolean]
}

object UnsafeLogF {
  def apply[F[_]: UnsafeLogF]: UnsafeLogF[F] = implicitly

  def fromLogger[F[_]: SyncSafe](logger: IzLogger): UnsafeLogF[F] = {
    new UnsafeLogF[F] {
      override def unsafeLog(entry: Entry): F[Unit] = {
        SyncSafe[F].syncSafe(logger.unsafeLog(entry))
      }

      override def acceptable(loggerId: LoggerId, logLevel: Level): F[Boolean] = {
        SyncSafe[F].syncSafe(logger.acceptable(loggerId, logLevel))
      }
    }
  }
}
