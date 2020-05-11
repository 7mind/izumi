package izumi.distage.docker

import java.io.File
import java.nio.channels.{FileChannel, OverlappingFileLockException}
import java.nio.file.StandardOpenOption

import izumi.distage.model.effect.DIEffect.syntax._
import izumi.distage.model.effect.{DIEffect, DIEffectAsync}
import izumi.logstage.api.IzLogger

import scala.concurrent.duration._

object FileLockMutex {

  def withLocalMutex[F[_], E](logger: IzLogger)(
    filename: String,
    waitFor: FiniteDuration,
    maxAttempts: Int,
  )(effect: F[E])(implicit
    F: DIEffect[F],
    P: DIEffectAsync[F],
  ): F[E] = {
    def acquireAndRun(channel: FileChannel, attempts: Int): F[E] = {
      F.maybeSuspend {
        logger.debug(s"Attempt ${attempts -> "num"} to acquire lock for $filename.")
        try {
          Option(channel.tryLock())
        } catch {
          case _: OverlappingFileLockException => None
        }
      }.flatMap {
        case Some(v) =>
          effect.guarantee(F.maybeSuspend(v.close()))
        case None if attempts < maxAttempts =>
          P.sleep(waitFor).flatMap(_ => acquireAndRun(channel, attempts + 1))
        case None =>
          logger.warn(s"Cannot acquire lock for image $filename after $attempts. This may lead to creation of a new container duplicate.")
          effect
      }
    }

      F.bracket(
        acquire = F.maybeSuspend {
          val tmpDir = System.getProperty("java.io.tmpdir")
          val file = new File(s"$tmpDir/$filename.tmp")
          val newFileCreated = file.createNewFile()
          if (newFileCreated) file.deleteOnExit()
          FileChannel.open(file.toPath, StandardOpenOption.WRITE)
        }
      )(release = ch => F.definitelyRecover(F.maybeSuspend(ch.close()))(_ => F.unit)) {
        acquireAndRun(_, 0)
      }
  }

}
