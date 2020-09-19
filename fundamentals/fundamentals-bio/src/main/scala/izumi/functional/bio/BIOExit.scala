package izumi.functional.bio

import zio.{Cause, Exit, FiberFailure}
import monix.bio

sealed trait BIOExit[+E, +A] {
  def map[B](f: A => B): BIOExit[E, B]
  def leftMap[E1](f: E => E1): BIOExit[E1, A]
  def flatMap[E1 >: E, B](f: A => BIOExit[E1, B]): BIOExit[E1, B]
}

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

    def map[E1](f: E => E1): Trace[E1]

    override final def toString: String = asString
  }
  object Trace {
    def empty: Trace[Nothing] = new Trace[Nothing] {
      override val asString: String = "<empty trace>"
      override def toThrowable: Throwable = new RuntimeException(asString)
      override def unsafeAttachTrace(conv: Nothing => Throwable): Throwable = toThrowable
      override def map[E1](f: Nothing => E1): Trace[E1] = this
    }

    final case class ZIOTrace[+E](cause: Cause[E]) extends Trace[E] {
      override def asString: String = cause.prettyPrint
      override def toThrowable: Throwable = FiberFailure(cause)
      override def unsafeAttachTrace(conv: E => Throwable): Throwable = cause.squashTraceWith {
        case t: Throwable => t
        case e => conv(e)
      }
      override def map[E1](f: E => E1): Trace[E1] = ZIOTrace(cause.map(f))
    }
  }

  final case class Success[+A](value: A) extends BIOExit[Nothing, A] {
    override def map[B](f: A => B): Success[B] = Success(f(value))
    override def leftMap[E1](f: Nothing => E1): this.type = this
    override def flatMap[E1 >: Nothing, B](f: A => BIOExit[E1, B]): BIOExit[E1, B] = f(value)
  }

  sealed trait Failure[+E] extends BIOExit[E, Nothing] {
    def trace: Trace[E]

    def toEither: Either[List[Throwable], E]
    def toEitherCompound: Either[Throwable, E]

    final def toThrowable(implicit ev: E <:< Throwable): Throwable = toEitherCompound.fold(identity, ev)
    final def toThrowable(conv: E => Throwable): Throwable = toEitherCompound.fold(identity, conv)
    final def unsafeAttachTrace(conv: E => Throwable): Throwable = trace.unsafeAttachTrace(conv)

    override final def map[B](f: Nothing => B): this.type = this
    override final def flatMap[E1 >: E, B](f: Nothing => BIOExit[E1, B]): this.type = this
  }

  final case class Error[+E](error: E, trace: Trace[E]) extends BIOExit.Failure[E] {
    override def toEither: Right[Nothing, E] = Right(error)
    override def toEitherCompound: Right[Nothing, E] = Right(error)
    override def leftMap[E1](f: E => E1): BIOExit[E1, Nothing] = Error[E1](f(error), trace.map(f))
  }

  final case class Termination(compoundException: Throwable, allExceptions: List[Throwable], trace: Trace[Nothing]) extends BIOExit.Failure[Nothing] {
    override def toEither: Left[List[Throwable], Nothing] = Left(allExceptions)
    override def toEitherCompound: Left[Throwable, Nothing] = Left(compoundException)
    override def leftMap[E1](f: Nothing => E1): this.type = this
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

  implicit lazy val BIOMonadBIOExit: BIOMonad[BIOExit] = new BIOMonad[BIOExit] {
    override final def pure[A](a: A): BIOExit[Nothing, A] = BIOExit.Success(a)
    override final def map[R, E, A, B](r: BIOExit[E, A])(f: A => B): BIOExit[E, B] = r.map(f)
    override final def flatMap[R, E, A, B](r: BIOExit[E, A])(f: A => BIOExit[E, B]): BIOExit[E, B] = r.flatMap(f)
  }

  implicit lazy val BIOBifunctorBIOExit: BIOBifunctor[BIOExit] = new BIOBifunctor[BIOExit] {
    override final val InnerF: BIOFunctor[BIOExit] = BIOMonadBIOExit
    override final def bimap[R, E, A, E2, A2](r: BIOExit[E, A])(f: E => E2, g: A => A2): BIOExit[E2, A2] = r.leftMap(f).map(g)
    override final def leftMap[R, E, A, E2](r: BIOExit[E, A])(f: E => E2): BIOExit[E2, A] = r.leftMap(f)
  }

  object MonixExit {
    @inline def toBIOExit[E, A](exit: Either[Option[bio.Cause[E]], A]): BIOExit[E, A] = {
      exit match {
        case Left(None) => Termination(new Throwable("The task was cancelled."), Trace.empty)
        case Left(Some(error)) => fromMonixCause(error)
        case Right(value) => Success(value)
      }
    }

    @inline def fromMonixCause[E](cause: bio.Cause[E]): BIOExit.Failure[E] = {
      cause match {
        case bio.Cause.Error(value) => BIOExit.Error(value, Trace.empty)
        case bio.Cause.Termination(value) => BIOExit.Termination(value, Trace.empty)
      }
    }
  }

}
