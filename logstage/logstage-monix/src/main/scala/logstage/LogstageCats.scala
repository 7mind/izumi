package logstage

import cats.Monad
import com.github.pshirshov.izumi.functional.mono.SyncSafe
import com.github.pshirshov.izumi.logstage.api.Log.{CustomContext, Entry, Message}
import cats.implicits._
import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer
import logstage.LogCreateIO.LogCreateIOSyncSafeInstance

object LogstageCats {

  def withDynamicContext[F[_]: Monad: SyncSafe](logger: IzLogger, dynamic: F[CustomContext]): LogIO[F] = {

    def withLogger[T](f: IzLogger => T) : F[T] = {
      dynamic.flatMap(ctx => SyncSafe[F].syncSafe(logger.withCustomContext(ctx)).map(f))
    }

    new LogCreateIOSyncSafeInstance[F] with LogIO[F] {

      override def log(entry: Entry): F[Unit] = {
        withLogger(_.log(entry))
      }

      override def log(logLevel: Level)(messageThunk: => Message)(implicit pos: CodePositionMaterializer):  F[Unit] = {
        withLogger(_.log(logLevel)(messageThunk))
      }
    }

  }
}
