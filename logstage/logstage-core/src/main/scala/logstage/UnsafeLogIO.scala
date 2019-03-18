package logstage

import com.github.pshirshov.izumi.functional.mono.SyncSafe
import com.github.pshirshov.izumi.logstage.api.AbstractLogger
import com.github.pshirshov.izumi.logstage.api.Log.{Entry, LoggerId}

trait UnsafeLogIO[+F[_]] {
  /** Log irrespective of the log level threshold */
  def unsafeLog(entry: Entry): F[Unit]

  /** Check if `loggerId` is not blacklisted and `logLevel` is above the configured threshold */
  def acceptable(loggerId: LoggerId, logLevel: Level): F[Boolean]
}

object UnsafeLogIO {
  def apply[F[_]: UnsafeLogIO]: UnsafeLogIO[F] = implicitly

  /**
    * Please enable `-Xsource:2.13` compiler option
    * If you're having trouble calling this method
    * with a single parameter `F`, e.g. `cats.effect.IO`
    *
    * @see bug https://github.com/scala/bug/issues/11435 for details
    *     [FIXED in 2.13 and in 2.12 with `-Xsource:2.13` flag]
    */
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
