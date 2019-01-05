package com.github.pshirshov.izumi.functional.bio

sealed trait BIOExit[+E, +A]

object BIOExit {
  final case class Success[+A](value: A) extends BIOExit[Nothing, A]

  sealed trait Failure[+E] extends BIOExit[E, Nothing] {
    def toEither: Either[List[Throwable], E]

    def toEitherCompound: Either[Throwable, E]
  }

  final case class Error[+E](error: E) extends BIOExit.Failure[E] {
    override def toEither: Right[List[Throwable], E] = Right(error)

    override def toEitherCompound: Either[Throwable, E] = Right(error)
  }

  final case class Termination(compoundException: Throwable, allExceptions: List[Throwable]) extends BIOExit.Failure[Nothing] {
    override def toEither: Either[List[Throwable], Nothing] = Left(allExceptions)

    override def toEitherCompound: Either[Throwable, Nothing] = Left(compoundException)
  }
}
