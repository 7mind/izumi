package logstage

import cats.Monad
import cats.syntax.flatMap._
import izumi.functional.mono.SyncSafe
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.AbstractLogger
import izumi.logstage.api.Log.{CustomContext, Entry, Message}
import logstage.UnsafeLogIO.UnsafeLogIOSyncSafeInstance

object LogstageCats {

  def withDynamicContext[F[_]: Monad: SyncSafe](logger: AbstractLogger)(dynamic: F[CustomContext]): LogIO[F] = {
    new UnsafeLogIOSyncSafeInstance[F](logger)(SyncSafe[F]) with LogIO[F] {
      override def unsafeLog(entry: Entry): F[Unit] = {
        withContextLogger(_.log(entry))
      }

      override def log(entry: Entry): F[Unit] = {
        withContextLogger(_.log(entry))
      }

      override def log(logLevel: Level)(messageThunk: => Message)(implicit pos: CodePositionMaterializer):  F[Unit] = {
        withContextLogger(_.log(logLevel)(messageThunk))
      }

      override def withCustomContext(context: CustomContext): LogIO[F] = {
        withDynamicContext(logger.withCustomContext(context))(dynamic)
      }

      private[this] def withContextLogger[T](f: AbstractLogger => T): F[T] = {
        dynamic.flatMap(ctx => SyncSafe[F].syncSafe(f(logger.withCustomContext(ctx))))
      }
    }
  }

}
