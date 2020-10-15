package izumi.distage.docker

import java.io.File
import java.nio.channels.{AsynchronousFileChannel, CompletionHandler, FileLock, OverlappingFileLockException}
import java.nio.file.StandardOpenOption

import izumi.distage.model.effect.QuasiEffect.syntax._
import izumi.distage.model.effect.{QuasiAsync, QuasiEffect}
import izumi.logstage.api.IzLogger

import scala.concurrent.duration._

object FileLockMutex {

  def withLocalMutex[F[_], E](
    logger: IzLogger
  )(filename: String,
    waitFor: FiniteDuration,
    maxAttempts: Int,
  )(effect: F[E]
  )(implicit
    F: QuasiEffect[F],
    P: QuasiAsync[F],
  ): F[E] = {
    def retryOnFileLock(eff: F[FileLock], attempts: Int = 0): F[Option[FileLock]] = {
      logger.debug(s"Attempt ${attempts -> "num"} out of $maxAttempts to acquire lock for $filename.")
      F.definitelyRecover(eff.map(Option(_))) {
        case _: OverlappingFileLockException =>
          if (attempts < maxAttempts) {
            P.sleep(waitFor).flatMap(_ => retryOnFileLock(eff, attempts + 1))
          } else {
            logger.warn(s"Cannot acquire lock for image $filename after $attempts. This may lead to creation of a new container duplicate.")
            F.pure(None)
          }
        case err =>
          F.fail(err)
      }
    }

    def createChannel: F[AsynchronousFileChannel] = F.maybeSuspend {
      val tmpDir = System.getProperty("java.io.tmpdir")
      val file = new File(s"$tmpDir/$filename.tmp")
      val newFileCreated = file.createNewFile()
      if (newFileCreated) file.deleteOnExit()
      AsynchronousFileChannel.open(file.toPath, StandardOpenOption.WRITE)
    }

    def acquireLock(channel: AsynchronousFileChannel): F[Option[FileLock]] = {
      retryOnFileLock {
        P.async[FileLock] {
          cb =>
            val handler = new CompletionHandler[FileLock, Unit] {
              override def completed(result: FileLock, attachment: Unit): Unit = cb(Right(result))
              override def failed(exc: Throwable, attachment: Unit): Unit = cb(Left(exc))
            }
            channel.lock((), handler)
        }
      }
    }

    F.bracket(
      acquire = createChannel
    )(release = channel => F.definitelyRecover(F.maybeSuspend(channel.close()))(_ => F.unit))(
      use = {
        channel =>
          F.bracket(
            acquire = acquireLock(channel)
          )(release = {
            case Some(lock) => F.maybeSuspend(lock.close())
            case None => F.unit
          })(use = _ => effect)
      }
    )
  }
}
