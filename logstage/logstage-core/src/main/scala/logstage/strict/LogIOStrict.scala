package logstage.strict

import izumi.functional.bio.{SyncSafe2, SyncSafe3}
import izumi.functional.mono.SyncSafe
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log._
import izumi.logstage.api.logger.{AbstractLogger, AbstractMacroStrictLoggerF, EncodingAwareAbstractLogIO, LogIORaw}
import izumi.logstage.api.rendering.StrictEncoded
import logstage.Level
import logstage.UnsafeLogIO.UnsafeLogIOSyncSafeInstance

import scala.language.implicitConversions

trait LogIOStrict[F[_]] extends EncodingAwareAbstractLogIO[F, StrictEncoded] with AbstractMacroStrictLoggerF[F] {
  override type Self[f[_]] = LogIOStrict[f]

  final def raw: LogIORaw[F, StrictEncoded] = new LogIORaw(this)

  override def widen[G[_]](implicit ev: F[_] <:< G[_]): LogIOStrict[G] = this.asInstanceOf[LogIOStrict[G]]
}

object LogIOStrict {
  @inline def apply[F[_]: LogIOStrict]: LogIOStrict[F] = implicitly

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
  implicit def limitedCovariance[F[+_, _], E](implicit log: LogIO2Strict[F]): LogIOStrict[F[E, ?]] = log.asInstanceOf[LogIOStrict[F[E, ?]]]
  implicit def covarianceConversion[G[_], F[_]](log: LogIOStrict[F])(implicit ev: F[_] <:< G[_]): LogIOStrict[G] = log.widen
}

object LogIO2Strict {
  @inline def apply[F[_, _]: LogIO2Strict]: LogIO2Strict[F] = implicitly

  @inline def fromLogger[F[_, _]: SyncSafe2](logger: AbstractLogger): LogIO2Strict[F] = LogIOStrict.fromLogger(logger)

  /**
    * Lets you refer to an implicit logger's methods without naming a variable
    *
    * {{{
    *   import logstage.LogIO2Strict.log
    *
    *   def fn[F[_, _]: LogIO2Strict]: F[Nothing, Unit] = {
    *     log.info(s"I'm logging with ${log}stage!")
    *   }
    * }}}
    */
  @inline def log[F[_, _]](implicit l: LogIO2Strict[F]): l.type = l
}

object LogIO3Strict {
  @inline def apply[F[_, _, _]: LogIO3Strict]: LogIO3Strict[F] = implicitly

  @inline def fromLogger[F[_, _, _]: SyncSafe3](logger: AbstractLogger): LogIO3Strict[F] = LogIOStrict.fromLogger(logger)

  /**
    * Lets you refer to an implicit logger's methods without naming a variable
    *
    * {{{
    *   import logstage.LogIO3.log
    *
    *   def fn[F[_, _, _]: LogIO3]: F[Any, Nothing, Unit] = {
    *     log.info(s"I'm logging with ${log}stage!")
    *   }
    * }}}
    */
  @inline def log[F[_, _, _]](implicit l: LogIO3Strict[F]): l.type = l
}
