package logstage

import cats.syntax.flatMap._
import com.github.pshirshov.izumi.functional.mono.SyncSafe
import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer
import com.github.pshirshov.izumi.logstage.api.AbstractLogger
import com.github.pshirshov.izumi.logstage.api.Log.{CustomContext, Entry, Message}
import logstage.LogCreateIO.LogCreateIOSyncSafeInstance

object LogstageCats {

  def withDynamicContext[F[_]: cats.Monad: SyncSafe](logger: AbstractLogger, dynamic: F[CustomContext]): LogIO[F] = {

    def withContextLogger[T](f: AbstractLogger => T): F[T] = {
      dynamic.flatMap(ctx => SyncSafe[F].syncSafe(f(logger.withCustomContext(ctx))))
    }

    new LogCreateIOSyncSafeInstance[F] with LogIO[F] {
      override def log(entry: Entry): F[Unit] = {
        withContextLogger(_.log(entry))
      }
      override def log(logLevel: Level)(messageThunk: => Message)(implicit pos: CodePositionMaterializer):  F[Unit] = {
        withContextLogger(_.log(logLevel)(messageThunk))
      }
      override def withCustomContext(context: CustomContext): LogIO[F] = {
        withDynamicContext[F](logger.withCustomContext(context), dynamic)
      }
    }
  }
}
