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

  /**
    * Emulate covariance. We're forced to employ these because
    * we can't make LogIO covariant, because covariant implicits
    * are broken (see scalac bug)
    *
    * Safe because `F` appears only in a covariant position
    *
    * @see https://github.com/scala/bug/issues/11427
    */
  implicit def limitedCovariance[F[+_, _], E](implicit log: UnsafeLogBIO[F]): UnsafeLogIO[F[E, ?]] = log.asInstanceOf[UnsafeLogIO[F[E, ?]]]
  implicit def covarianceConversion[G[_], F[_]](log: UnsafeLogIO[F])(implicit ev: F[_] <:< G[_]): UnsafeLogIO[G] = { val _ = ev; log.asInstanceOf[UnsafeLogIO[G]] }
}
