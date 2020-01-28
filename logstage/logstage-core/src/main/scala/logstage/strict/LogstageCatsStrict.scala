package logstage.strict

import cats.Monad
import cats.syntax.flatMap._
import izumi.functional.mono.SyncSafe
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log.{CustomContext, Entry, Level, Message}
import izumi.logstage.api.logger.AbstractLogger
import logstage.UnsafeLogIO.UnsafeLogIOSyncSafeInstance

object LogstageCatsStrict {

  def withDynamicContextStrict[F[_]: Monad: SyncSafe](logger: AbstractLogger)(dynamic: F[CustomContext]): LogIOStrict[F] = {
    new WrappedLogIOStrict[F](logger)(SyncSafe[F]) {
      override def withCustomContext(context: CustomContext): LogIOStrict[F] = {
        withDynamicContextStrict(logger.withCustomContext(context))(dynamic)
      }

      override protected[this] def wrap[T](f: AbstractLogger => T): F[T] = {
        dynamic.flatMap(ctx => SyncSafe[F].syncSafe(f(logger.withCustomContext(ctx))))
      }
    }
  }

  private[logstage] abstract class WrappedLogIOStrict[F[_]]
  (
    logger: AbstractLogger,
  )(F: SyncSafe[F]) extends UnsafeLogIOSyncSafeInstance[F](logger)(F) with LogIOStrict[F] {

    protected[this] def wrap[A](f: AbstractLogger => A): F[A]

    override final def unsafeLog(entry: Entry): F[Unit] = wrap(_.unsafeLog(entry))
    override final def log(entry: Entry): F[Unit] = wrap(_.log(entry))
    override final def log(logLevel: Level)(messageThunk: => Message)(implicit pos: CodePositionMaterializer): F[Unit] = wrap(_.log(logLevel)(messageThunk))
  }
}
