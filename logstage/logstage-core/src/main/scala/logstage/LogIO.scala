package logstage

import com.github.pshirshov.izumi.functional.mono.SyncSafe
import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer
import com.github.pshirshov.izumi.logstage.api.AbstractLogger
import com.github.pshirshov.izumi.logstage.api.Log._
import com.github.pshirshov.izumi.logstage.macros.LogIOMacros._
import logstage.LogCreateIO.LogCreateIOSyncSafeInstance

import scala.language.experimental.macros
import scala.language.implicitConversions

trait LogIO[F[_]] extends LogCreateIO[F] {
  def log(entry: Entry): F[Unit]
  def log(logLevel: Level)(messageThunk: => Message)(implicit pos: CodePositionMaterializer): F[Unit]

  /** Aliases for [[log]] that look better in Intellij */
  final def trace(message: String): F[Unit] = macro scTraceMacro[F]
  final def debug(message: String): F[Unit] = macro scDebugMacro[F]
  final def info(message: String): F[Unit] = macro scInfoMacro[F]
  final def warn(message: String): F[Unit] = macro scWarnMacro[F]
  final def error(message: String): F[Unit] = macro scErrorMacro[F]
  final def crit(message: String): F[Unit] = macro scCritMacro[F]
}

object LogIO {
  def apply[F[_]: LogIO]: LogIO[F] = implicitly

  def fromLogger[F[_]: SyncSafe](logger: AbstractLogger): LogIO[F] = {
    new LogCreateIOSyncSafeInstance[F] with LogIO[F] {
      override def log(entry: Entry): F[Unit] = {
        F.syncSafe(logger.log(entry))
      }

      override def log(logLevel: Level)(messageThunk: => Message)(implicit pos: CodePositionMaterializer): F[Unit] = {
        F.syncSafe(logger.log(logLevel)(messageThunk))
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
  implicit def limitedCovariance[F[+_, _], E](implicit log: LogBIO[F]): LogIO[F[E, ?]] = log.asInstanceOf[LogIO[F[E, ?]]]
  implicit def covarianceConversion[G[_], F[_]](log: LogIO[F])(implicit ev: F[_] <:< G[_]): LogIO[G] = { val _ = ev; log.asInstanceOf[LogIO[G]] }

  //  implicit def covariance[G[x] >: F[x], F[_]](implicit log: LogIO[F]): LogIO[G] = log.asInstanceOf[LogIO[G]]
  //  implicit def covariance[G[_], F[_]](implicit log: LogIO[F], ev: F[_] <:< G[_]): LogIO[G] = log.asInstanceOf[LogIO[G]] //
  //  implicit def covarianceConversion[G[x] >: F[x], F[_]](log: LogIO[F])(implicit ev: F[Any] <:< G[Any]): LogIO[G] = log.asInstanceOf[LogIO[G]]
  //  implicit def covarianceConversion[G[x] >: F[x], F[_]](log: LogIO[F])(implicit ev: F[Any] <:< G[Any]): LogIO[G] = log.asInstanceOf[LogIO[G]]
  //  implicit def covarianceConversion[E1 >: E, E, F[+_, _]](log: LogIO[F[E, ?]]): LogIO[F[E1, ?]] = log.asInstanceOf[LogIO[F[E1, ?]]]
}
