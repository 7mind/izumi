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
    // MUST be by-name because of QuasiIO[Identity]
    lockAlreadyExistedLog: => F[Unit],
  )(fail: Int => F[A],
    succ: FileLock => F[A],
  )(implicit
    F: QuasiIO[F],
    P: QuasiAsync[F],
    T: QuasiTemporal[F],
  ): F[A] = {
    allocate[F, A](filename, retryWait, maxAttempts, attemptLog, lockAlreadyExistedLog)(fail, succ).use(F.pure)
  }

  def allocate[F[_], A](
    filename: String,
    retryWait: FiniteDuration,
    maxAttempts: Int,
    attemptLog: (Int, Int) => F[Unit],
    // MUST be by-name because of QuasiIO[Identity]
    lockAlreadyExistedLog: => F[Unit],
  )(fail: Int => F[A],
    succ: FileLock => F[A],
  )(implicit
    F: QuasiIO[F],
    P: QuasiAsync[F],
    T: QuasiTemporal[F],
  ): Lifecycle[F, A] = {
    def retryOnFileLock(
      // MUST be by-name because of QuasiIO[Identity]
      doAcquire: => F[FileLock]
    ): F[(A, Option[FileLock])] = {
      F.tailRecM(0) {
        attempts =>
          F.when(attempts != 0) {
            attemptLog(attempts, maxAttempts)
          }.flatMap {
              _ =>
                F.definitelyRecoverUnsafeIgnoreTrace[Either[Int, (A, Option[FileLock])]](
                  doAcquire.flatMap(lock => succ(lock).map(a => Right((a, Some(lock)))))
                )(recover = {
                  case _: OverlappingFileLockException =>
                    if (attempts < maxAttempts) {
                      T.sleep(retryWait).map(_ => Left(attempts + 1))
                    } else {
                      fail(attempts).map(a => Right((a, None)))
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

    def acquireLock(channel: AsynchronousFileChannel): F[(A, Option[FileLock])] = {
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
            case (_, Some(lock)) => F.maybeSuspend(lock.close())
            case (_, None) => F.unit
          })
      }.map(_._1)
  }

}
