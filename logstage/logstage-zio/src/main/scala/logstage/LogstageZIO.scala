package logstage

import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer
import com.github.pshirshov.izumi.logstage.api.Log.{Entry, Message}
import logstage.LogCreateIO.LogCreateIOSyncSafeInstance
import scalaz.zio.IO

object LogstageZIO {

  def withFiberId(logger: IzLogger): LogBIO[IO] = {
    new LogCreateIOSyncSafeInstance[IO[Nothing, ?]] with LogIO[IO[Nothing, ?]] {

      override def log(entry: Entry): IO[Nothing, Unit] = {
        withFiberId(_.log(entry))
      }

      override def log(logLevel: Level)(messageThunk: => Message)(implicit pos: CodePositionMaterializer):  IO[Nothing, Unit] = {
        withFiberId(_.log(logLevel)(messageThunk))
      }

      private def withFiberId[T](f : IzLogger => T) : IO[Nothing, T] = {
        IO.descriptor.map(_.id).flatMap(id => IO.sync(f(logger.apply("fiberId" -> id))))
      }
    }
  }
}
