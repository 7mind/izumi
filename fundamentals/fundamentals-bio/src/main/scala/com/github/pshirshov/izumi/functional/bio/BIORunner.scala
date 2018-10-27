package com.github.pshirshov.izumi.functional.bio

import java.util.concurrent.ExecutorService

import scalaz.zio.Errors.TerminatedFiber
import scalaz.zio.{ExitResult, IO, RTS}

import scala.language.higherKinds
import scala.util.{Failure, Success, Try}

trait BIORunner[F[_, _]] {
  def unsafeRun[E, A](io: F[E, A]): A

  def unsafeRunSyncAsEither[E, A](io: F[E, A]): Try[Either[E, A]]

  def unsafeRunAsyncAsEither[E, A](io: F[E, A])(callback: Try[Either[E, A]] => Unit): Unit
}

object BIORunner {
  def apply[F[_, _] : BIORunner]: BIORunner[F] = implicitly

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

  private def toEither[A, E](result: ExitResult[E, A]): Try[Either[E, A]] = {
    result match {
      case ExitResult.Completed(v)    => Success(Right(v))
      case ExitResult.Failed(e, _)    => Success(Left(e))
      case ExitResult.Terminated(Nil) => Failure(TerminatedFiber)
      case ExitResult.Terminated(t)   => Failure(t.head)
    }
  }
}
