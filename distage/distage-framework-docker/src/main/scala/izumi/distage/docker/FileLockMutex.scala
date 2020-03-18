package izumi.distage.docker

import java.io.File
import java.nio.channels.{FileChannel, OverlappingFileLockException}
import java.nio.file.StandardOpenOption

import izumi.distage.model.effect.DIEffect.syntax._
import izumi.distage.model.effect.{DIEffect, DIEffectAsync}
import izumi.logstage.api.IzLogger

import scala.concurrent.duration._

object FileLockMutex {
  def withLocalMutex[F[_]: DIEffect: DIEffectAsync, E](
    filename: String,
    logger: IzLogger,
    waitFor: FiniteDuration = 1.second,
    maxAttempts: Int = 10
  )(eff: F[E]): F[E] = {
    def acquireAndRun(chanel: FileChannel, attempts: Int = 0): F[E] = {
      DIEffect[F].maybeSuspend {
        logger.debug(s"Attempt ${attempts -> "num"} to acquire lock for $filename.")
        try {
          Option(chanel.tryLock())
        } catch {
          case _: OverlappingFileLockException => None
        }
      }.flatMap {
        case Some(v) =>
          eff.guarantee(DIEffect[F].maybeSuspend(v.close()))
        case None if attempts < maxAttempts =>
          DIEffectAsync[F].sleep(waitFor).flatMap(_ => acquireAndRun(chanel, attempts + 1))
        case _ =>
          logger.warn(s"Cannot acquire lock for image $filename after $attempts. This may lead to creation of a new container duplicate.")
          eff
      }
    }

    val tmpDir = System.getProperty("java.io.tmpdir")
    val file = new File(s"$tmpDir/$filename.tmp")
    file.createNewFile()
    DIEffect[F].bracket(DIEffect[F].maybeSuspend(FileChannel.open(file.toPath, StandardOpenOption.WRITE))) {
      ch =>
        DIEffect[F].definitelyRecover {
          DIEffect[F].maybeSuspend {
            ch.close()
            file.delete()
            ()
          }
        }(_ => DIEffect[F].unit)
    } {
      acquireAndRun(_)
    }
  }
}
