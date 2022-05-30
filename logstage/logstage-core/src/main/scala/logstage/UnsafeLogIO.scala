package logstage

import izumi.functional.bio.{SyncSafe1, SyncSafe2, SyncSafe3}
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log.{Entry, LoggerId}
import izumi.logstage.api.logger.AbstractLogger
import logstage.LogCreateIO.LogCreateIOSyncSafeInstance

import scala.annotation.unused
import scala.language.implicitConversions

trait UnsafeLogIO[F[_]] extends LogCreateIO[F] {
  /** Log irrespective of the log level threshold */
  def unsafeLog(entry: Entry): F[Unit]

  /** Check if `loggerId` is not blacklisted and `logLevel` is at or above the configured threshold */
  def acceptable(loggerId: LoggerId, logLevel: Level): F[Boolean]

  /** Check if this class/package is allowed to log messages at or above `logLevel` */
  def acceptable(logLevel: Level)(implicit pos: CodePositionMaterializer): F[Boolean]

  override def widen[G[_]](implicit @unused ev: F[?] <:< G[?]): UnsafeLogIO[G] = this.asInstanceOf[UnsafeLogIO[G]]
}

object UnsafeLogIO extends LowPriorityUnsafeLogIOInstances {
  def apply[F[_]: UnsafeLogIO]: UnsafeLogIO[F] = implicitly

  def fromLogger[F[_]: SyncSafe1](logger: AbstractLogger): UnsafeLogIO[F] = new UnsafeLogIOSyncSafeInstance[F](logger)(SyncSafe1[F])

  class UnsafeLogIOSyncSafeInstance[F[_]](logger: AbstractLogger)(F: SyncSafe1[F]) extends LogCreateIOSyncSafeInstance[F](F) with UnsafeLogIO[F] {
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

  implicit def covarianceConversion[G[_], F[_]](log: UnsafeLogIO[F])(implicit ev: F[?] <:< G[?]): UnsafeLogIO[G] = log.widen
}

sealed trait LowPriorityUnsafeLogIOInstances {
  /**
    * Emulate covariance. We're forced to employ these because
    * we can't make LogIO covariant, because covariant implicits
    * are broken (see scalac bug)
    *
    * Safe because `F` appears only in a covariant position
    *
    * @see https://github.com/scala/bug/issues/11427
    */
  implicit def limitedCovariance2[F[+_, _], E](implicit log: UnsafeLogIO2[F]): UnsafeLogIO[F[E, _]] = log.widen
  implicit def limitedCovariance3[F[-_, +_, _], R, E](implicit log: UnsafeLogIO3[F]): UnsafeLogIO[F[R, E, _]] = log.widen
}

object UnsafeLogIO2 {
  @inline def apply[F[_, _]: UnsafeLogIO2]: UnsafeLogIO2[F] = implicitly

  @inline def fromLogger[F[_, _]: SyncSafe2](logger: AbstractLogger): UnsafeLogIO2[F] = UnsafeLogIO.fromLogger(logger)
}

object UnsafeLogIO3 {
  @inline def apply[F[_, _, _]: UnsafeLogIO3]: UnsafeLogIO3[F] = implicitly

  @inline def fromLogger[F[_, _, _]: SyncSafe3](logger: AbstractLogger): UnsafeLogIO3[F] = UnsafeLogIO.fromLogger(logger)
}
