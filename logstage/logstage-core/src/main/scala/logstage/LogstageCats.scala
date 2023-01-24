package logstage

import cats.Monad
import cats.syntax.flatMap.*
import izumi.functional.bio.SyncSafe1
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log.{CustomContext, Entry, Message}
import izumi.logstage.api.logger.{AbstractLogger, AbstractLoggerF}
import logstage.UnsafeLogIO.{UnsafeLogIOSyncSafeInstance, UnsafeLogIOSyncSafeInstanceF}

object LogstageCats {
  def withDynamicContext[F[_]: Monad: SyncSafe1](logger: AbstractLogger)(dynamic: F[CustomContext]): LogIO[F] = {
    new WrappedLogIO[F](logger)(SyncSafe1[F]) {
      override def withCustomContext(context: CustomContext): LogIO[F] = {
        withDynamicContext(logger.withCustomContext(context))(dynamic)
      }

      override protected[this] def wrap[T](f: AbstractLogger => T): F[T] = {
        dynamic.flatMap(ctx => SyncSafe1[F].syncSafe(f(logger.withCustomContext(ctx))))
      }
    }
  }

  def withDynamicContext[F[_]: Monad: SyncSafe1](logger: AbstractLoggerF[F])(dynamic: F[CustomContext]): LogIO[F] = {
    new WrappedLogIOF[F](logger)(SyncSafe1[F]) {

      override def log(entry: Entry): F[Unit] = logger.log(entry)

      override def log(logLevel: Log.Level)(messageThunk: => Message)(implicit pos: CodePositionMaterializer): F[Unit] = logger.log(logLevel)(messageThunk)

      override def withCustomContext(context: CustomContext): LogIO[F] = {
        withDynamicContext(logger.withCustomContext(context))(dynamic)
      }
    }
  }

  private[logstage] abstract class WrappedLogIO[F[_]](
    logger: AbstractLogger
  )(F: SyncSafe1[F]
  ) extends UnsafeLogIOSyncSafeInstance[F](logger)(F)
    with LogIO[F] {
    protected[this] def wrap[A](f: AbstractLogger => A): F[A]

    override final def unsafeLog(entry: Entry): F[Unit] = wrap(_.unsafeLog(entry))
    override final def log(entry: Entry): F[Unit] = wrap(_.log(entry))
    override final def log(logLevel: Level)(messageThunk: => Message)(implicit pos: CodePositionMaterializer): F[Unit] = wrap(_.log(logLevel)(messageThunk))
  }

  private[logstage] abstract class WrappedLogIOF[F[_]](
    logger: AbstractLoggerF[F]
  )(F: SyncSafe1[F]
  ) extends UnsafeLogIOSyncSafeInstanceF[F](logger)(F)
    with LogIO[F] {}
}
