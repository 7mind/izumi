package logstage

import izumi.functional.bio.SyncSafe2
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.AbstractLogger
import izumi.logstage.api.Log.{CustomContext, Entry, Message}
import logstage.UnsafeLogIO.UnsafeLogIOSyncSafeInstance
import zio.IO

object LogstageZIO {

  def withFiberId(logger: AbstractLogger): LogBIO[IO] = {
    new UnsafeLogIOSyncSafeInstance[IO[Nothing, ?]](logger)(SyncSafe2[IO]) with LogIO[IO[Nothing, ?]] {
      override def unsafeLog(entry: Entry): IO[Nothing, Unit] = {
        withFiberContext(_.unsafeLog(entry))
      }

      override def log(entry: Entry): IO[Nothing, Unit] = {
        withFiberContext(_.log(entry))
      }

      override def log(logLevel: Level)(messageThunk: => Message)(implicit pos: CodePositionMaterializer): IO[Nothing, Unit] = {
        withFiberContext(_.log(logLevel)(messageThunk))
      }

      override def withCustomContext(context: CustomContext): LogIO[IO[Nothing, ?]] = {
        withFiberId(logger.withCustomContext(context))
      }

      private[this] def withFiberContext[T](f: AbstractLogger => T): IO[Nothing, T] = {
        IO.descriptorWith {
          descriptor =>
            val fiberLogger = logger.withCustomContext(CustomContext("fiberId" -> descriptor.id))
            IO.effectTotal(f(fiberLogger))
        }
      }
    }
  }

}
