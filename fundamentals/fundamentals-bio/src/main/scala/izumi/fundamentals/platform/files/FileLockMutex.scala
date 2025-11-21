package izumi.fundamentals.platform.files

import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.QuasiIO.syntax.*
import izumi.functional.quasi.{QuasiAsync, QuasiIO, QuasiTemporal}

import java.io.File
import java.nio.channels.{AsynchronousFileChannel, CompletionHandler, FileLock, OverlappingFileLockException}
import java.nio.file.StandardOpenOption
import scala.concurrent.duration.*

object FileLockMutex {

  def withLocalMutex[F[_], A](
    filename: String,
    retryWait: FiniteDuration,
    maxAttempts: Int,
    attemptLog: (Int, Int) => F[Unit],
    failLog: Int => F[Unit],
    // MUST be by-name because of QuasiIO[Identity]
    lockAlreadyExistedLog: => F[Unit],
  )(effect:
    // MUST be by-name because of QuasiIO[Identity]
    => F[A]
  )(implicit
    F: QuasiIO[F],
    P: QuasiAsync[F],
    T: QuasiTemporal[F],
  ): F[A] = {
    allocate[F](filename, retryWait, maxAttempts, attemptLog, failLog, lockAlreadyExistedLog).use(_ => effect)
  }

  def allocate[F[_]](
    filename: String,
    retryWait: FiniteDuration,
    maxAttempts: Int,
    attemptLog: (Int, Int) => F[Unit],
    failLog: Int => F[Unit],
    // MUST be by-name because of QuasiIO[Identity]
    lockAlreadyExistedLog: => F[Unit],
  )(implicit
    F: QuasiIO[F],
    P: QuasiAsync[F],
    T: QuasiTemporal[F],
  ): Lifecycle[F, Option[FileLock]] = {
    def retryOnFileLock(
      // MUST be by-name because of QuasiIO[Identity]
      doAcquire: => F[FileLock]
    ): F[Option[FileLock]] = {
      F.tailRecM(0) {
        attempts =>
          F.when(attempts != 0) {
            attemptLog(attempts, maxAttempts)
          }.flatMap {
              _ =>
                F.definitelyRecoverUnsafeIgnoreTrace[Either[Int, Option[FileLock]]](
                  doAcquire.map(lock => Right(Option(lock)))
                )(recover = {
                  case _: OverlappingFileLockException =>
                    if (attempts < maxAttempts) {
                      T.sleep(retryWait).map(_ => Left(attempts + 1))
                    } else {
                      failLog(attempts).map(_ => Right(None))
                    }
                  case err =>
                    F.fail(err)
                })
            }
      }
    }

    def createChannel(): F[AsynchronousFileChannel] = F.suspendF {
      val tmpDir = System.getProperty("java.io.tmpdir")
      val file = new File(s"$tmpDir/$filename.tmp")
      val newFileCreated = file.createNewFile()
      (if (newFileCreated) {
         F.maybeSuspend(file.deleteOnExit())
       } else {
         lockAlreadyExistedLog
       }).flatMap {
        _ => F.maybeSuspend(AsynchronousFileChannel.open(file.toPath, StandardOpenOption.WRITE))
      }
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

    Lifecycle
      .make(
        acquire = createChannel()
      )(release = {
        channel =>
          F.definitelyRecoverUnsafeIgnoreTrace(F.maybeSuspend(channel.close()))(_ => F.unit)
      }).flatMap {
        channel =>
          Lifecycle.make(
            acquire = acquireLock(channel)
          )(release = {
            case Some(lock) => F.maybeSuspend(lock.close())
            case None => F.unit
          })
      }
  }

}
