package logstage

import izumi.functional.mono.SyncSafe
import izumi.fundamentals.platform.language.{CodePositionMaterializer, unused}
import izumi.logstage.api.Log.{Entry, LoggerId}
import izumi.logstage.api.logger.AbstractLogger
import logstage.LogCreateIO.LogCreateIOSyncSafeInstance

import scala.language.implicitConversions

trait UnsafeLogIO[F[_]] extends LogCreateIO[F] {
  /** Log irrespective of the log level threshold */
  def unsafeLog(entry: Entry): F[Unit]

  /** Check if `loggerId` is not blacklisted and `logLevel` is at or above the configured threshold */
  def acceptable(loggerId: LoggerId, logLevel: Level): F[Boolean]

  /** Check if this class/package is allowed to log messages at or above `logLevel` */
  def acceptable(logLevel: Level)(implicit pos: CodePositionMaterializer): F[Boolean]

  override def widen[G[_]](implicit @unused ev: F[_] <:< G[_]): UnsafeLogIO[G] = this.asInstanceOf[UnsafeLogIO[G]]
}

object UnsafeLogIO {
  def apply[F[_]: UnsafeLogIO]: UnsafeLogIO[F] = implicitly

  def fromLogger[F[_]: SyncSafe](logger: AbstractLogger): UnsafeLogIO[F] = new UnsafeLogIOSyncSafeInstance[F](logger)(SyncSafe[F])

  class UnsafeLogIOSyncSafeInstance[F[_]](logger: AbstractLogger)(F: SyncSafe[F]) extends LogCreateIOSyncSafeInstance[F](F) with UnsafeLogIO[F] {
    override def unsafeLog(entry: Entry): F[Unit] = {
      F.syncSafe(logger.unsafeLog(entry))
    }

    override def acceptable(loggerId: LoggerId, logLevel: Level): F[Boolean] = {
      F.syncSafe(logger.acceptable(loggerId, logLevel))
    }

    override def acceptable(logLevel: Level)(implicit pos: CodePositionMaterializer): F[Boolean] = {
      F.syncSafe(logger.acceptable(logLevel))
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
  implicit def limitedCovariance[F[+_, _], E](implicit log: UnsafeLogBIO[F]): UnsafeLogIO[F[E, ?]] = log.widen
  implicit def covarianceConversion[G[_], F[_]](log: UnsafeLogIO[F])(implicit @unused ev: F[_] <:< G[_]): UnsafeLogIO[G] = log.widen
}
