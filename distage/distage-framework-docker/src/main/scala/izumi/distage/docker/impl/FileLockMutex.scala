package izumi.distage.docker.impl

import izumi.functional.quasi.QuasiIO.syntax.*
import izumi.functional.quasi.{QuasiAsync, QuasiIO}
import izumi.logstage.api.IzLogger

import java.io.File
import java.nio.channels.{AsynchronousFileChannel, CompletionHandler, FileLock, OverlappingFileLockException}
import java.nio.file.StandardOpenOption
import scala.concurrent.duration.*

object FileLockMutex {

  def withLocalMutex[F[_], A](
    logger: IzLogger
  )(filename: String,
    retryWait: FiniteDuration,
    maxAttempts: Int,
  )(effect:
    // MUST be by-name because of QuasiIO[Identity]
    => F[A]
  )(implicit
    F: QuasiIO[F],
    P: QuasiAsync[F],
  ): F[A] = {
    def retryOnFileLock(
      // MUST be by-name because of QuasiIO[Identity]
      doAcquire: => F[FileLock]
    ): F[Option[FileLock]] = {
      F.tailRecM(0) {
        attempts =>
          if (attempts != 0) {
            logger.info(s"Attempt ${attempts -> "num"} out of $maxAttempts to acquire file lock for image $filename.")
          }
          F.definitelyRecover[Either[Int, Option[FileLock]]](
            doAcquire.map(lock => Right(Option(lock)))
          )(recover = {
            case _: OverlappingFileLockException =>
              if (attempts < maxAttempts) {
                P.sleep(retryWait).map(_ => Left(attempts + 1))
              } else {
                logger.warn(s"Cannot acquire file lock for image $filename after $attempts. This may lead to creation of a new duplicate container")
                F.pure(Right(None))
              }
            case err =>
              F.fail(err)
          })
      }
    }

    def createChannel(): F[AsynchronousFileChannel] = F.maybeSuspend {
      val tmpDir = System.getProperty("java.io.tmpdir")
      val file = new File(s"$tmpDir/$filename.tmp")
      val newFileCreated = file.createNewFile()
      if (newFileCreated) {
        file.deleteOnExit()
      } else {
        logger.debug(s"File lock already existed for image $filename")
      }
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
      acquire = createChannel()
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
