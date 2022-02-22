package logstage

import cats.Monad
import cats.syntax.flatMap.*
import izumi.functional.bio.SyncSafe1
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log.{CustomContext, Entry, Message}
import izumi.logstage.api.logger.AbstractLogger
import logstage.UnsafeLogIO.UnsafeLogIOSyncSafeInstance

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

}
