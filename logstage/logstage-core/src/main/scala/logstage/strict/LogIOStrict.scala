package logstage.strict

import izumi.functional.mono.SyncSafe
import izumi.fundamentals.platform.language.{CodePositionMaterializer, unused}
import izumi.logstage.api.Log._
import izumi.logstage.api.logger.{AbstractLogger, AbstractMacroStrictLoggerF}
import izumi.logstage.api.rendering.StrictEncoded
import logstage.UnsafeLogIO.UnsafeLogIOSyncSafeInstance
import logstage.{Level, UnsafeLogIO}

import scala.language.implicitConversions

trait LogIOStrict[F[_]] extends UnsafeLogIO[F] with AbstractMacroStrictLoggerF[F] {
  def log(entry: Entry): F[Unit]
  def log(logLevel: Level)(messageThunk: => Message)(implicit pos: CodePositionMaterializer): F[Unit]
  def withCustomContext(context: CustomContext): LogIOStrict[F]

  final def withCustomContext(context: (String, StrictEncoded)*): LogIOStrict[F] = withCustomContextMap(context.toMap)
  final def withCustomContextMap(context: Map[String, StrictEncoded]): LogIOStrict[F] = withCustomContext(CustomContext.fromMap(context))
  final def apply(context: CustomContext): LogIOStrict[F] = withCustomContext(context)
  final def apply(context: (String, StrictEncoded)*): LogIOStrict[F] = withCustomContextMap(context.toMap)
}

object LogIOStrict {
  def apply[F[_]: LogIOStrict]: LogIOStrict[F] = implicitly

  /**
    * Lets you refer to an implicit logger's methods without naming a variable
    *
    * {{{
    *   import logstage.LogIOStrict.log
    *
    *   def fn[F[_]: LogIOStrict]: F[Unit] = {
    *     log.info(s"I'm logging with ${log}stage!")
    *   }
    * }}}
    */
  @inline def log[F[_]](implicit l: LogIOStrict[F]): l.type = l

  def fromLogger[F[_]: SyncSafe](logger: AbstractLogger): LogIOStrict[F] = {
    new UnsafeLogIOSyncSafeInstance[F](logger)(SyncSafe[F]) with LogIOStrict[F] {
      override def log(entry: Entry): F[Unit] = {
        F.syncSafe(logger.log(entry))
      }

      override def log(logLevel: Level)(messageThunk: => Message)(implicit pos: CodePositionMaterializer): F[Unit] = {
        F.syncSafe(logger.log(logLevel)(messageThunk))
      }

      override def withCustomContext(context: CustomContext): LogIOStrict[F] = {
        fromLogger[F](logger.withCustomContext(context))
      }
    }
  }

  /**
    * Emulate covariance. We're forced to employ these because
    * we can't make LogIOStrict covariant, because covariant implicits
    * are broken (see scalac bug)
    *
    * Safe because `F` appears only in a covariant position
    *
    * @see https://github.com/scala/bug/issues/11427
    */
  implicit def limitedCovariance[F[+_, _], E](implicit log: LogBIOStrict[F]): LogIOStrict[F[E, ?]] = log.asInstanceOf[LogIOStrict[F[E, ?]]]
  implicit def covarianceConversion[G[_], F[_]](log: LogIOStrict[F])(implicit @unused ev: F[_] <:< G[_]): LogIOStrict[G] = log.asInstanceOf[LogIOStrict[G]]
}
