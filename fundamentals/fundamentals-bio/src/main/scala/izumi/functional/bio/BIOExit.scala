package izumi.functional.bio

import zio.{Cause, Exit, FiberFailure}

sealed trait BIOExit[+E, +A]

object BIOExit {

  trait Trace {
    def asString: String
    def toThrowable: Throwable

    override final def toString: String = asString
  }

  object Trace {
    def zioTrace(cause: Cause[_]): Trace = ZIOTrace(cause)
    def empty: Trace = new Trace {
      val asString: String = "<empty trace>"
      def toThrowable: Throwable = new RuntimeException(asString)
    }

    final case class ZIOTrace(cause: Cause[_]) extends Trace {
      override def asString: String = cause.prettyPrint
      override def toThrowable: Throwable = FiberFailure(cause)
    }
  }

  final case class Success[+A](value: A) extends BIOExit[Nothing, A]

  sealed trait Failure[+E] extends BIOExit[E, Nothing] {
    def trace: Trace

    def toEither: Either[List[Throwable], E]
    def toEitherCompound: Either[Throwable, E]

    final def toThrowable(implicit ev: E <:< Throwable): Throwable = toEitherCompound.fold(identity, ev)
    final def toThrowable(conv: E => Throwable): Throwable = toEitherCompound.fold(identity, conv)
  }

  final case class Error[+E](error: E, trace: Trace) extends BIOExit.Failure[E] {
    override def toEither: Right[Nothing, E] = Right(error)
    override def toEitherCompound: Right[Nothing, E] = Right(error)
  }

  final case class Termination(compoundException: Throwable, allExceptions: List[Throwable], trace: Trace) extends BIOExit.Failure[Nothing] {
    override def toEither: Left[List[Throwable], Nothing] = Left(allExceptions)
    override def toEitherCompound: Left[Throwable, Nothing] = Left(compoundException)
  }

  object Termination {
    def apply(exception: Throwable, trace: Trace): Termination = new Termination(exception, List(exception), trace)
  }

  object ZIOExit {

    @inline def toBIOExit[E, A](result: Exit[E, A]): BIOExit[E, A] = result match {
      case Exit.Success(v) =>
        Success(v)
      case Exit.Failure(cause) =>
        toBIOExit(cause)
    }

    @inline def toBIOExit[E](result: Cause[E]): BIOExit.Failure[E] = {
      val trace = Trace.zioTrace(result)

      result.failureOrCause match {
        case Left(err) =>
          Error(err, trace)
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
          Termination(compound, exceptions, trace)
      }
    }

  }

}
