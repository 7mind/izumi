package com.github.pshirshov.izumi.functional.bio

sealed trait BIOExit[+E, +A]

object BIOExit {
  final case class Success[+A](value: A) extends BIOExit[Nothing, A]

  sealed trait Failure[+E] extends BIOExit[E, Nothing]
  final case class Error[+E](error: E) extends BIOExit.Failure[E]
  final case class Termination(compoundException: Throwable, allExceptions: List[Throwable]) extends BIOExit.Failure[Nothing]

  def toEither[E](fail: BIOExit.Failure[E]): Either[List[Throwable], E] = {
    fail match {
      case Error(error) => Right(error)
      case Termination(_, exceptions) => Left(exceptions)
    }
  }
}
