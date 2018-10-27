package com.github.pshirshov.izumi.functional.bio

import scalaz.zio.Errors.TerminatedFiber
import scalaz.zio.{ExitResult, Fiber, IO}

import scala.util.{Failure, Success, Try}

trait BIOFiber[F[_, _], E, A] {
  def join: F[E, A]

  def observe: F[Nothing, Try[Either[E, A]]]

  def interrupt: F[Nothing, Unit]
}

object BIOFiber {
  def fromZIO[E, A](f: Fiber[E, A]): BIOFiber[IO, E, A] =
    new BIOFiber[IO, E, A] {
      override val join: IO[E, A] = f.join
      override val observe: IO[Nothing, Try[Either[E, A]]] = f.observe.map {
        case ExitResult.Completed(v) => Success(Right(v))
        case ExitResult.Failed(e, _) => Success(Left(e))
        case ExitResult.Terminated(Nil) => Failure(TerminatedFiber)
        case ExitResult.Terminated(t) => Failure(t.head)
      }
      override def interrupt: IO[Nothing, Unit] = f.interrupt
    }
}
