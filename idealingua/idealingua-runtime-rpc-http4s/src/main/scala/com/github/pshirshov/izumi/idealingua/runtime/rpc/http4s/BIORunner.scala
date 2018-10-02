package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import java.util.concurrent.ExecutorService

import scalaz.zio.{ExitResult, IO, RTS}

import scala.language.higherKinds
import scala.util.{Failure, Success, Try}

trait BIORunner[BIO[_, _]] {
  def unsafeRun[E, A](io: BIO[E, A]): A

  def unsafeRunSyncAsEither[E, A](io: BIO[E, A]): Try[Either[E, A]]

  def unsafeRunAsyncAsEither[E, A](io: BIO[E, A])(callback: Try[Either[E, A]] => Unit): Unit
}

object BIORunner {
  def apply[BIO[_, _] : BIORunner]: BIORunner[BIO] = implicitly

  def createZIO(threadPool: ExecutorService): BIORunner[IO] = new ZIORunnerBase(threadPool)

  class ZIORunnerBase(override val threadPool: ExecutorService)
    extends BIORunner[IO]
      with RTS {
    override def defaultHandler: List[Throwable] => IO[Nothing, Unit] = {
      _ => IO.sync(())
    }

    def unsafeRunAsyncAsEither[E, A](io: IO[E, A])(callback: Try[Either[E, A]] => Unit): Unit = {
      unsafeRunAsync(io)(exitResult => callback(toEither(exitResult)))
    }

    override def unsafeRunSyncAsEither[E, A](io: IO[E, A]): Try[Either[E, A]] = {
      val result = unsafeRunSync(io)
      toEither(result)
    }
  }

  private def toEither[A, E](result: ExitResult[E, A]) = {
    result match {
      case ExitResult.Completed(v) => Success(Right(v))
      case ExitResult.Failed(e, _) => Success(Left(e))
      case ExitResult.Terminated(t) => Failure(t.head)
    }
  }
}
