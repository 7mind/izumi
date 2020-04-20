package izumi.functional.bio

import zio.{Cause, Exit, FiberFailure}

sealed trait BIOExit[+E, +A]

object BIOExit {

  trait Trace[+E] {
    def asString: String
    def toThrowable: Throwable

    /** Unsafely Mutate the contained Throwable to attach this Trace's debugging information to it.
      *
      * NOTE: may mutate arbitrary Throwables contained in the trace, discard all throwables that came from the same source
      * @param conv convert any contained typed errors into a Throwable
      */
    def unsafeAttachTrace(conv: E => Throwable): Throwable

    override final def toString: String = asString
  }

  object Trace {
    @deprecated("Use ZIOTrace", "will be removed after 0.10.5")
    private[bio] def zioTrace[E](cause: Cause[E]): Trace[E] = ZIOTrace(cause)
    def empty: Trace[Nothing] = new Trace[Nothing] {
      val asString: String = "<empty trace>"
      def toThrowable: Throwable = new RuntimeException(asString)
      def unsafeAttachTrace(conv: Nothing => Throwable): Throwable = toThrowable
    }

    final case class ZIOTrace[+E](cause: Cause[E]) extends Trace[E] {
      override def asString: String = cause.prettyPrint
      override def toThrowable: Throwable = FiberFailure(cause)
      override def unsafeAttachTrace(conv: E => Throwable): Throwable = cause.squashTraceWith(conv)
    }
  }

  final case class Success[+A](value: A) extends BIOExit[Nothing, A]

  sealed trait Failure[+E] extends BIOExit[E, Nothing] {
    def trace: Trace[E]

    def toEither: Either[List[Throwable], E]
    def toEitherCompound: Either[Throwable, E]

    final def toThrowable(implicit ev: E <:< Throwable): Throwable = toEitherCompound.fold(identity, ev)
    final def toThrowable(conv: E => Throwable): Throwable = toEitherCompound.fold(identity, conv)
    final def unsafeAttachTrace(conv: E => Throwable): Throwable = trace.unsafeAttachTrace(conv)
  }

  final case class Error[+E](error: E, trace: Trace[E]) extends BIOExit.Failure[E] {
    override def toEither: Right[Nothing, E] = Right(error)
    override def toEitherCompound: Right[Nothing, E] = Right(error)
  }

  final case class Termination(compoundException: Throwable, allExceptions: List[Throwable], trace: Trace[Nothing]) extends BIOExit.Failure[Nothing] {
    override def toEither: Left[List[Throwable], Nothing] = Left(allExceptions)
    override def toEitherCompound: Left[Throwable, Nothing] = Left(compoundException)
  }

  object Termination {
    def apply(exception: Throwable, trace: Trace[Nothing]): Termination = new Termination(exception, List(exception), trace)
  }

  object ZIOExit {

    @inline def toBIOExit[E, A](result: Exit[E, A]): BIOExit[E, A] = result match {
      case Exit.Success(v) =>
        Success(v)
      case Exit.Failure(cause) =>
        toBIOExit(cause)
    }

    @inline def toBIOExit[E](result: Cause[E]): BIOExit.Failure[E] = {
      result.failureOrCause match {
        case Left(err) =>
          Error(err, Trace.ZIOTrace(result))
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
          Termination(compound, exceptions, Trace.ZIOTrace(cause))
      }
    }

  }

}
