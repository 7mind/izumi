package logstage

import izumi.functional.mono.SyncSafe
import izumi.fundamentals.platform.language.{CodePositionMaterializer, unused}
import izumi.logstage.api.Log._
import izumi.logstage.api.logger.{AbstractLogger, AbstractMacroLoggerF}
import izumi.logstage.api.rendering.AnyEncoded
import logstage.UnsafeLogIO.UnsafeLogIOSyncSafeInstance

import scala.language.implicitConversions

trait LogIO[F[_]] extends UnsafeLogIO[F] with AbstractMacroLoggerF[F] {
  def log(entry: Entry): F[Unit]
  def log(logLevel: Level)(messageThunk: => Message)(implicit pos: CodePositionMaterializer): F[Unit]
  def withCustomContext(context: CustomContext): LogIO[F]

  final def withCustomContext(context: (String, AnyEncoded)*): LogIO[F] = withCustomContextMap(context.toMap)
  final def withCustomContextMap(context: Map[String, AnyEncoded]): LogIO[F] = withCustomContext(CustomContext.fromMap(context))
  final def apply(context: CustomContext): LogIO[F] = withCustomContext(context)
  final def apply(context: (String, AnyEncoded)*): LogIO[F] = withCustomContextMap(context.toMap)
}

object LogIO {
  @inline def apply[F[_]](implicit l: LogIO[F]): l.type = l

  /**
    * Lets you refer to an implicit logger's methods without naming a variable
    *
    * {{{
    *   import logstage.LogIO.log
    *
    *   def fn[F[_]: LogIO]: F[Unit] = {
    *     log.info(s"I'm logging with ${log}stage!")
    *   }
    * }}}
    */
  @inline def log[F[_]](implicit l: LogIO[F]): l.type = l

  def fromLogger[F[_]: SyncSafe](logger: AbstractLogger): LogIO[F] = {
    new UnsafeLogIOSyncSafeInstance[F](logger)(SyncSafe[F]) with LogIO[F] {
      override def log(entry: Entry): F[Unit] = {
        F.syncSafe(logger.log(entry))
      }

      override def log(logLevel: Level)(messageThunk: => Message)(implicit pos: CodePositionMaterializer): F[Unit] = {
        F.syncSafe(logger.log(logLevel)(messageThunk))
      }

      override def withCustomContext(context: CustomContext): LogIO[F] = {
        fromLogger[F](logger.withCustomContext(context))
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
  implicit def covarianceConversion[G[_], F[_]](log: LogIO[F])(implicit @unused ev: F[_] <:< G[_]): LogIO[G] = log.asInstanceOf[LogIO[G]]
}
