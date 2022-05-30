package logstage.strict

import izumi.functional.bio.{SyncSafe1, SyncSafe2, SyncSafe3}
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log.*
import izumi.logstage.api.logger.{AbstractLogger, AbstractMacroStrictLogIO, EncodingAwareAbstractLogIO, LogIORaw}
import izumi.logstage.api.rendering.StrictEncoded
import logstage.Level
import logstage.UnsafeLogIO.UnsafeLogIOSyncSafeInstance

import scala.language.implicitConversions

trait LogIOStrict[F[_]] extends EncodingAwareAbstractLogIO[F, StrictEncoded] with AbstractMacroStrictLogIO[F] {
  override type Self[f[_]] = LogIOStrict[f]

  final def raw: LogIORaw[F, StrictEncoded] = new LogIORaw(this)

  override def widen[G[_]](implicit ev: F[?] <:< G[?]): LogIOStrict[G] = this.asInstanceOf[LogIOStrict[G]]
}

object LogIOStrict extends LowPriorityLogIOStrictInstances {
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

  def fromLogger[F[_]: SyncSafe1](logger: AbstractLogger): LogIOStrict[F] = {
    new UnsafeLogIOSyncSafeInstance[F](logger)(SyncSafe1[F]) with LogIOStrict[F] {
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

  implicit def covarianceConversion[G[_], F[_]](log: LogIOStrict[F])(implicit ev: F[?] <:< G[?]): LogIOStrict[G] = log.widen
}

sealed trait LowPriorityLogIOStrictInstances {
  /**
    * Emulate covariance. We're forced to employ these because
    * we can't make LogIOStrict covariant, because covariant implicits
    * are broken (see scalac bug)
    *
    * Safe because `F` appears only in a covariant position
    *
    * @see https://github.com/scala/bug/issues/11427
    */
  implicit def limitedCovariance2[F[+_, _], E](implicit log: LogIO2Strict[F]): LogIOStrict[F[E, _]] = log.asInstanceOf[LogIOStrict[F[E, _]]]
  implicit def limitedCovariance3[F[-_, +_, _], R, E](implicit log: LogIO3Strict[F]): LogIOStrict[F[R, E, _]] = log.widen
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
