package izumi.fundamentals.platform.files

import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.QuasiIO.syntax.*
import izumi.functional.quasi.{QuasiAsync, QuasiIO, QuasiTemporal}

import java.io.{File, IOException}
import java.nio.channels.{AsynchronousFileChannel, CompletionHandler, FileChannel, FileLock, OverlappingFileLockException}
import java.nio.file.StandardOpenOption
import scala.concurrent.Promise
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
    val minimumBackoff = 10.millis
    val maximumBackoff = 1.second

    def waitForExternal(tryAcquire: => F[Option[FileLock]]): F[FileLock] = {
      F.tailRecM[FiniteDuration, FileLock](retryWait.max(minimumBackoff).min(maximumBackoff)) {
        delay =>
          tryAcquire.flatMap {
            case Some(lock) => F.pure(Right(lock))
            case None => T.sleep(delay).map(_ => Left((delay + delay).min(maximumBackoff)))
          }
      }
    }

    def acquireLock(channel: AsynchronousFileChannel): F[FileLock] = {
      def submit = F
        .maybeSuspend {
          val result = Promise[FileLock]()
          channel.lock(
            (),
            new CompletionHandler[FileLock, Unit] {
              override def completed(lock: FileLock, attachment: Unit): Unit = { val _ = result.success(lock) }
              override def failed(error: Throwable, attachment: Unit): Unit = { val _ = result.failure(error) }
            },
          )
          result.future
        }.flatMap(future => P.fromFuture(future))

      waitForExternal {
        F.redeem[FileLock, Option[FileLock]](submit)(
          {
            case error: IOException if error.getClass == classOf[IOException] && channel.isOpen =>
              F.definitelyRecoverUnsafeIgnoreTrace(F.maybeSuspend(Option(channel.tryLock()))) {
                case probe: IOException => error.addSuppressed(probe); F.fail(error)
                case other => F.fail(other)
              }
            case other => F.fail(other)
          },
          lock => F.pure(Some(lock)),
        )
      }
    }

    type Attempt = Either[Int, (Int, FileLock)]

    def retryOnFileLock(
      start: Int,
      onFirstOverlap: => F[Unit],
    )(doAcquire: => F[FileLock]
    ): F[Attempt] = {
      F.tailRecM[Int, Attempt](start) {
        attempts =>
          F.redeem[FileLock, Either[Int, Attempt]](doAcquire)(
            {
              case _: OverlappingFileLockException =>
                F.when(attempts == start)(onFirstOverlap).flatMap {
                  _ =>
                    if (attempts < maxAttempts) {
                      val next = attempts + 1
                      T.sleep(retryWait).flatMap(_ => attemptLog(next, maxAttempts)).map(_ => Left(next))
                    } else {
                      F.pure(Right(Left(attempts)))
                    }
                }
              case other => F.fail(other)
            },
            lock => F.pure(Right(Right((attempts, lock)))),
          )
      }
    }

    def openMutex(file: File, reportedOverlap: Boolean): F[AsynchronousFileChannel] = F.maybeSuspend(file.createNewFile()).flatMap {
      created =>
        F.when(!created && !reportedOverlap)(lockAlreadyExistedLog).flatMap {
          _ =>
            F.maybeSuspend(AsynchronousFileChannel.open(file.toPath, StandardOpenOption.WRITE))
        }
    }

    Lifecycle.liftF(F.maybeSuspend(new File(System.getProperty("java.io.tmpdir"), s"$filename.tmp").getCanonicalFile)).flatMap {
      file =>
        /* Keep both files: unlinking at release or JVM exit lets a newcomer lock a different inode while another process still holds or awaits the old one.
         * Keeping native lock files is current accepted practice in python-filelock on Unix and Apache Lucene's NativeFSLockFactory:
         * - https://github.com/tox-dev/filelock/blob/f80f6a8d09315ad314a388523b1e470d1f08c986/src/filelock/_unix.py#L81-L88 (https://github.com/tox-dev/filelock/pull/577)
         * - https://github.com/apache/lucene/blob/a4c11a89a5e976a2109f7bbe56f6b65537e43936/lucene/core/src/java/org/apache/lucene/store/NativeFSLockFactory.java#L41-L47 (https://github.com/apache/lucene/issues/6674)
         * systemd validates st_nlink with fstat on the locked descriptor before accepting it, then unlinks under lock. FileChannel exposes no fstat; JDK 22+ FFM needs native access.
         * - https://github.com/systemd/systemd/blob/38a509e85814924455e2a81e52e8e0388cf83281/src/basic/fs-util.c#L1562-L1583
         * - https://github.com/systemd/systemd/blob/38a509e85814924455e2a81e52e8e0388cf83281/src/basic/lock-util.c#L84-L103
         * izumi #971 moved deletion to JVM exit, but the creator need not be the last holder (https://github.com/7mind/izumi/pull/971):
         * - https://github.com/7mind/izumi/blob/b87d681c63a57a44e206215e386e24bcce37ae70/distage/distage-framework-docker/src/main/scala/izumi/distage/docker/FileLockMutex.scala#L43-L48
         */
        val sidecar = file.toPath.resolveSibling(s"${file.getName}.gate")
        def openGate = F.maybeSuspend(FileChannel.open(sidecar, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE))
        Lifecycle.make(openGate)(gate => F.maybeSuspend(gate.close())).flatMap {
          gate =>
            Lifecycle.liftF(retryOnFileLock(0, lockAlreadyExistedLog)(waitForExternal(F.maybeSuspend(Option(gate.tryLock(0L, 1L, true)))))).flatMap {
              case Left(attempts) => Lifecycle.liftF(fail(attempts))
              case Right((used, _)) =>
                Lifecycle
                  .make(openMutex(file, used != 0))(
                    channel =>
                      F.maybeSuspend {
                        try channel.close()
                        finally gate.close()
                      }
                  ).evalMap {
                    channel =>
                      retryOnFileLock(used, F.unit)(acquireLock(channel)).flatMap {
                        case Left(attempts) => fail(attempts)
                        case Right((_, lock)) => succ(lock)
                      }
                  }
            }
        }
    }
  }

}
