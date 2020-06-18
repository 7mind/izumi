package izumi.distage.docker

import java.io.File
import java.nio.channels.{AsynchronousFileChannel, CompletionHandler, FileLock, OverlappingFileLockException}
import java.nio.file.StandardOpenOption

import izumi.distage.model.effect.DIEffect.syntax._
import izumi.distage.model.effect.{DIEffect, DIEffectAsync}
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
    F: DIEffect[F],
    P: DIEffectAsync[F],
  ): F[E] = {
    def retryOnFileLock(eff: F[Option[FileLock]], attempts: Int = 0): F[Option[FileLock]] = {
      logger.debug(s"Attempt ${attempts -> "num"} out of $maxAttempts to acquire lock for $filename.")
      F.definitelyRecover(eff) {
        case _: OverlappingFileLockException =>
          if (attempts < maxAttempts) {
            P.sleep(waitFor).flatMap(_ => retryOnFileLock(eff, attempts + 1))
          } else {
            logger.warn(s"Cannot acquire lock for image $filename after $attempts. This may lead to creation of a new container duplicate.")
            F.pure(None)
          }
        case err => F.fail(err)
      }
    }

    F.bracket(
      acquire = F.maybeSuspend {
        val tmpDir = System.getProperty("java.io.tmpdir")
        val file = new File(s"$tmpDir/$filename.tmp")
        val newFileCreated = file.createNewFile()
        if (newFileCreated) file.deleteOnExit()
        AsynchronousFileChannel.open(file.toPath, StandardOpenOption.WRITE)
      }
    )(release = channel => F.definitelyRecover(F.maybeSuspend(channel.close()))(_ => F.unit)) {
      channel =>
        retryOnFileLock {
          P.async[Option[FileLock]] {
            cb =>
              val handler = new CompletionHandler[FileLock, Unit] {
                override def completed(result: FileLock, attachment: Unit): Unit = cb(Right(Some(result)))
                override def failed(exc: Throwable, attachment: Unit): Unit = cb(Left(exc))
              }
              channel.lock((), handler)
          }
        }.flatMap {
          case Some(lock) => effect.guarantee(F.maybeSuspend(lock.close()))
          case None => effect
        }
    }
  }

}
