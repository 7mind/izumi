package com.github.pshirshov.izumi.functional.bio

import scalaz.zio.{Exit, FiberFailure}

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

  trait ZIO {
    @inline def toBIOExit[E, A](result: Exit[E, A]): BIOExit[E, A] = result match {
      case Exit.Success(v) =>
        Success(v)
      case Exit.Failure(cause) =>
        toBIOExit(cause)
    }

    @inline def toBIOExit[E](result: Exit.Cause[E]): BIOExit.Failure[E] = {
      result.failureOrCause match {
        case Left(err) =>
          Error(err)
        case Right(cause) =>
          val unchecked = cause.defects
          val exceptions = if (cause.interrupted) {
            new InterruptedException :: unchecked
          } else {
            unchecked
          }
          val compound = exceptions match {
            case e :: Nil => e
            case _ => FiberFailure(cause)
          }
          Termination(compound, exceptions)
      }
    }

    @inline def failureToCause[E](errEither: BIOExit.Failure[E]): Exit.Cause[E] = {
      errEither match {
        case Error(err) =>
          Exit.Cause.Fail(err)
        case Termination(_, Nil) =>
          Exit.Cause.Die(new IllegalArgumentException(s"Unexpected empty cause list from sandboxWith: $errEither"))
        case Termination(_, exceptions) =>
          exceptions.map {
            case _: InterruptedException => Exit.Cause.Interrupt
            case e => Exit.Cause.Die(e)
          }.reduce(_ ++ _)
      }
    }
  }
  object ZIO extends ZIO

}
