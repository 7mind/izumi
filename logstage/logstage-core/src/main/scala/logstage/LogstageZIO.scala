package logstage

import com.github.pshirshov.izumi.functional.bio.SyncSafe2
import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer
import com.github.pshirshov.izumi.logstage.api.AbstractLogger
import com.github.pshirshov.izumi.logstage.api.Log.{CustomContext, Entry, Message}
import logstage.LogCreateIO.LogCreateIOSyncSafeInstance
import zio.IO

object LogstageZIO {

  def withFiberId(logger: AbstractLogger): LogBIO[IO] = {
    new LogCreateIOSyncSafeInstance[IO[Nothing, ?]](SyncSafe2[IO]) with LogIO[IO[Nothing, ?]] {

      override def log(entry: Entry): IO[Nothing, Unit] = {
        withFiberContext(_.log(entry))
      }

      override def log(logLevel: Level)(messageThunk: => Message)(implicit pos: CodePositionMaterializer):  IO[Nothing, Unit] = {
        withFiberContext(_.log(logLevel)(messageThunk))
      }

      override def withCustomContext(context: CustomContext): LogIO[IO[Nothing, ?]] = {
        withFiberId(logger.withCustomContext(context))
      }

      private[this] def withFiberContext[T](f : AbstractLogger => T): IO[Nothing, T] = {
        IO.descriptor.map(_.id).flatMap(id => IO.effectTotal(f(logger.withCustomContext(CustomContext("fiberId" -> id)))))
      }
    }
  }
}
