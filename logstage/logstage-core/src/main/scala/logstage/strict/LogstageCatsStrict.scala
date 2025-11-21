package logstage.strict

import cats.Monad
import cats.syntax.flatMap.*
import izumi.functional.bio.SyncSafe1
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log.{CustomContext, Entry, Level, Message}
import izumi.logstage.api.logger.{AbstractLogger, AbstractLoggerF}
import logstage.UnsafeLogIO.{UnsafeLogIOSyncSafeInstance, UnsafeLogIOSyncSafeInstanceF}

object LogstageCatsStrict {

  def withDynamicContextStrict[F[_]: Monad: SyncSafe1](logger: AbstractLogger)(dynamic: F[CustomContext]): LogIOStrict[F] = {
    new WrappedLogIOStrict[F](logger)(SyncSafe1[F]) {
      override def withCustomContext(context: CustomContext): LogIOStrict[F] = {
        withDynamicContextStrict(logger.withCustomContext(context))(dynamic)
      }

      override protected def wrap[T](f: AbstractLogger => T): F[T] = {
        dynamic.flatMap(ctx => SyncSafe1[F].syncSafe(f(logger.withCustomContext(ctx))))
      }
    }
  }

  def withDynamicContextStrict[F[_]: Monad: SyncSafe1](logger: AbstractLoggerF[F])(dynamic: F[CustomContext]): LogIOStrict[F] = {
    new WrappedLogIOStrictF[F](logger)(SyncSafe1[F]) {
      override def withCustomContext(context: CustomContext): LogIOStrict[F] = {
        withDynamicContextStrict(logger.withCustomContext(context))(dynamic)
      }

      override protected def wrap[A](f: AbstractLoggerF[F] => F[A]): F[A] = {
        dynamic.flatMap(ctx => f(logger.withCustomContext(ctx)))
      }
    }
  }

  private[logstage] abstract class WrappedLogIOStrict[F[_]](
    logger: AbstractLogger
  )(F: SyncSafe1[F]
  ) extends UnsafeLogIOSyncSafeInstance[F](logger)(F)
    with LogIOStrict[F] {

    protected def wrap[A](f: AbstractLogger => A): F[A]

    override final def unsafeLog(entry: Entry): F[Unit] = wrap(_.unsafeLog(entry))
    override final def log(entry: Entry): F[Unit] = wrap(_.log(entry))
    override final def log(logLevel: Level)(messageThunk: => Message)(implicit pos: CodePositionMaterializer): F[Unit] = wrap(_.log(logLevel)(messageThunk))
  }

  private[logstage] abstract class WrappedLogIOStrictF[F[_]](
    logger: AbstractLoggerF[F]
  )(F: SyncSafe1[F]
  ) extends UnsafeLogIOSyncSafeInstanceF[F](logger)(F)
    with LogIOStrict[F] {

    protected def wrap[A](f: AbstractLoggerF[F] => F[A]): F[A]

    override final def unsafeLog(entry: Entry): F[Unit] = wrap(_.unsafeLog(entry))
    override final def log(entry: Entry): F[Unit] = wrap(_.log(entry))
    override final def log(logLevel: Level)(messageThunk: => Message)(implicit pos: CodePositionMaterializer): F[Unit] = wrap(_.log(logLevel)(messageThunk))
  }
}
