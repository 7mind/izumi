package com.github.pshirshov.izumi.functional.bio

sealed trait BIOExit[+E, +A]

object BIOExit {
  final case class Success[+A](value: A) extends BIOExit[Nothing, A]

  sealed trait Failure[+E] extends BIOExit[E, Nothing] {
    def toEither: Either[List[Throwable], E]

    def toEitherCompound: Either[Throwable, E]

    final def toThrowable(implicit ev: E <:< Throwable): Throwable = toEitherCompound.fold(identity, ev)
  }

  final case class Error[+E](error: E) extends BIOExit.Failure[E] {
    override def toEither: Right[List[Throwable], E] = Right(error)

    override def toEitherCompound: Right[Throwable, E] = Right(error)
  }

  final case class Termination(compoundException: Throwable, allExceptions: List[Throwable]) extends BIOExit.Failure[Nothing] {
    override def toEither: Left[List[Throwable], Nothing] = Left(allExceptions)

    override def toEitherCompound: Left[Throwable, Nothing] = Left(compoundException)
  }

  object Termination {
    def apply(exception: Throwable): Termination = new Termination(exception, List(exception))
  }
}
