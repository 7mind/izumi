package izumi.functional.bio

import cats.effect.kernel.Outcome
import zio.Cause

sealed trait Exit[+E, +A] {
  def map[B](f: A => B): Exit[E, B]
  def leftMap[E1](f: E => E1): Exit[E1, A]
  def flatMap[E1 >: E, B](f: A => Exit[E1, B]): Exit[E1, B]
  def toThrowableEither(implicit ev: E <:< Throwable): Either[Throwable, A]
}

object Exit {

  trait Trace[+E] {
    def asString: String
    def toThrowable: Throwable

    /** Unsafely Mutate the contained Throwable to attach this Trace's debugging information to it.
      *
      * @note may mutate arbitrary Throwables contained in the trace, discard all throwables that came from the same source
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

    final case class ZIOTrace[+E](cause: zio.Cause[E]) extends Trace[E] {
      override def asString: String = cause.prettyPrint
      override def toThrowable: Throwable = zio.FiberFailure(cause)
      override def unsafeAttachTrace(conv: E => Throwable): Throwable = cause.squashTraceWith {
        case t: Throwable => t
        case e => conv(e)
      }
      override def map[E1](f: E => E1): Trace[E1] = ZIOTrace(cause.map(f))
    }
  }

  final case class Success[+A](value: A) extends Exit[Nothing, A] {
    override def map[B](f: A => B): Success[B] = Success(f(value))
    override def leftMap[E1](f: Nothing => E1): this.type = this
    override def flatMap[E1 >: Nothing, B](f: A => Exit[E1, B]): Exit[E1, B] = f(value)
    override def toThrowableEither(implicit ev: Nothing <:< Throwable): Either[Throwable, A] = Right(value)
  }

  sealed trait Failure[+E] extends Exit[E, Nothing] {
    def trace: Trace[E]

    def toEither: Either[List[Throwable], E]
    def toEitherCompound: Either[Throwable, E]

    final def toThrowable(implicit ev: E <:< Throwable): Throwable = toEitherCompound.fold(identity, ev)
    final def toThrowable(conv: E => Throwable): Throwable = toEitherCompound.fold(identity, conv)
    final def unsafeAttachTrace(conv: E => Throwable): Throwable = trace.unsafeAttachTrace(conv)

    override final def map[B](f: Nothing => B): this.type = this
    override final def flatMap[E1 >: E, B](f: Nothing => Exit[E1, B]): this.type = this
  }

  final case class Error[+E](error: E, trace: Trace[E]) extends Exit.Failure[E] {
    override def toEither: Right[Nothing, E] = Right(error)
    override def toEitherCompound: Right[Nothing, E] = Right(error)
    override def toThrowableEither(implicit ev: E <:< Throwable): Either[Throwable, Nothing] = Left(ev(error))
    override def leftMap[E1](f: E => E1): Exit[E1, Nothing] = Error[E1](f(error), trace.map(f))
  }

  final case class Termination(compoundException: Throwable, allExceptions: List[Throwable], trace: Trace[Nothing]) extends Exit.Failure[Nothing] {
    override def toEither: Left[List[Throwable], Nothing] = Left(allExceptions)
    override def toEitherCompound: Left[Throwable, Nothing] = Left(compoundException)
    override def toThrowableEither(implicit ev: Nothing <:< Throwable): Either[Throwable, Nothing] = Left(compoundException)
    override def leftMap[E1](f: Nothing => E1): this.type = this
  }
  object Termination {
    def apply(exception: Throwable, trace: Trace[Nothing]): Termination = new Termination(exception, List(exception), trace)
  }

  final case class Interruption(compoundException: Throwable, trace: Trace[Nothing]) extends Exit.Failure[Nothing] {
    override def toEither: Left[List[Throwable], Nothing] = Left(List(compoundException))
    override def toEitherCompound: Left[Throwable, Nothing] = Left(compoundException)
    override def toThrowableEither(implicit ev: Nothing <:< Throwable): Either[Throwable, Nothing] = Left(compoundException)
    override def leftMap[E1](f: Nothing => E1): this.type = this
  }
  object Interruption {
    def apply(trace: Trace[Nothing]): Interruption = Interruption(trace.toThrowable, trace)
  }

  object ZIOExit {
    def toExit[E, A](result: zio.Exit[E, A]): Exit[E, A] = result match {
      case zio.Exit.Success(v) =>
        Success(v)
      case zio.Exit.Failure(cause) =>
        toExit(cause)
    }

    def toExit[E](result: zio.Cause[E]): Exit.Failure[E] = {
      result.failureOrCause match {
        case Left(err) =>
          Error(err, Trace.ZIOTrace(result))
        case Right(cause) if cause.interrupted =>
          Interruption(Trace.ZIOTrace(cause))
        case Right(cause) =>
          val exceptions = cause.defects
          val compound = exceptions match {
            case e :: Nil => e
            case _ => zio.FiberFailure(cause)
          }
          Termination(compound, exceptions, Trace.ZIOTrace(cause))
      }
    }

    def fromExit[E, A](exit: Exit[E, A]): zio.Exit[E, A] = exit match {
      case Success(value) =>
        zio.Exit.Success(value)
      case failure: Failure[E] =>
        zio.Exit.Failure(causeFromExit(failure))
    }

    def causeFromExit[E](failure: Failure[E]): Cause[E] = {
      failure.trace match {
        case Trace.ZIOTrace(cause) =>
          cause
        case _ =>
          failure match {
            case Error(error, _) =>
              zio.Cause.fail(error)
            case Termination(_, headException :: tailExceptions, _) =>
              tailExceptions.foldLeft(zio.Cause.die(headException))(_ ++ zio.Cause.die(_))
            case Termination(compoundException, _, _) =>
              zio.Cause.die(compoundException)
            case Interruption(_, _) =>
              zio.Cause.interrupt(zio.Fiber.Id.None)
          }
      }
    }
  }

//  object MonixExit {
//    def toExit[E, A](exit: Either[Option[bio.Cause[E]], A]): Exit[E, A] = {
//      exit match {
//        case Left(None) => Interruption(new InterruptedException("The task was cancelled."), Trace.empty)
//        case Left(Some(error)) => toExit(error)
//        case Right(value) => Success(value)
//      }
//    }
//
//    def toExit[E, A](exit: Either[bio.Cause[E], A])(implicit d: DummyImplicit): Exit[E, A] = {
//      exit match {
//        case Left(error) => toExit(error)
//        case Right(value) => Success(value)
//      }
//    }
//
//    def toExit[E](cause: bio.Cause[E]): Exit.Failure[E] = {
//      cause match {
//        case bio.Cause.Error(value) => Exit.Error(value, Trace.empty)
//        case bio.Cause.Termination(value) => Exit.Termination(value, Trace.empty)
//      }
//    }
//  }

  object CatsExit {
    def exitToOutcomeThrowable[F[+_, +_], A](exit: Exit[Throwable, A])(implicit F: Applicative2[F]): Outcome[F[Throwable, +_], Throwable, A] = {
      toOutcomeThrowable(F.pure, exit)
    }
    def toOutcomeThrowable[F[_], A](pure: A => F[A], exit: Exit[Throwable, A]): Outcome[F, Throwable, A] = exit match {
      case Exit.Success(b) => Outcome.succeeded(pure(b))
      case Exit.Interruption(_, _) => Outcome.canceled
      case error @ Error(_, _) => Outcome.errored(error.toThrowable)
      case termination @ Termination(_, _, _) => Outcome.errored(termination.toThrowable)
    }
  }

  implicit lazy val ExitInstances: Monad2[Exit] & Bifunctor2[Exit] = new Monad2[Exit] with Bifunctor2[Exit] {
    override final val InnerF: Functor2[Exit] = this
    override final def pure[A](a: A): Exit[Nothing, A] = Exit.Success(a)
    override final def map[R, E, A, B](r: Exit[E, A])(f: A => B): Exit[E, B] = r.map(f)
    override final def bimap[R, E, A, E2, A2](r: Exit[E, A])(f: E => E2, g: A => A2): Exit[E2, A2] = r.leftMap(f).map(g)
    override final def leftMap[R, E, A, E2](r: Exit[E, A])(f: E => E2): Exit[E2, A] = r.leftMap(f)
    override final def flatMap[R, E, A, B](r: Exit[E, A])(f: A => Exit[E, B]): Exit[E, B] = r.flatMap(f)
  }

}
