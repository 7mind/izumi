package com.github.pshirshov.izumi.functional.bio

import scalaz.zio.IO

import scala.util.Try

trait BIOFunctor[F[_, +_]] {
  def map[E, A, B](r: F[E, A])(f: A => B): F[E, B]
  def void[E, A](r: F[E, A]): F[E, Unit] = map(r)(_ => ())
}

object BIOFunctor {
  def apply[F[_, _]: BIOFunctor]: BIOFunctor[F] = implicitly
}

trait BIOBifunctor[F[+_, +_]] extends BIOFunctor[F] {
  def bimap[E, A, E2, A2](r: F[E, A])(f: E => E2, g: A => A2): F[E2, A2]
  @inline def leftMap[E, A, E2](r: F[E, A])(f: E => E2): F[E2, A] = bimap(r)(f, identity)
}

object BIOBifunctor {
  def apply[F[_, _]: BIOBifunctor]: BIOBifunctor[F] = implicitly
}

trait BIOApplicative[F[+_, +_]] extends BIOBifunctor[F] {
  def now[A](a: A): F[Nothing, A]

  /** execute two operations in order, map their results */
  def map2[E, A, E2 >: E, B, C](firstOp: F[E, A], secondOp: => F[E2, B])(f: (A, B) => C): F[E2, C]

  /** execute two operations in order, return result of second operation */
  def *>[E, A, E2 >: E, B](firstOp: F[E, A], secondOp: => F[E2, B]): F[E2, B]

  /** execute two operations in order, same as `*>`, but return result of first operation */
  def <*[E, A, E2 >: E, B](firstOp: F[E, A], secondOp: => F[E2, B]): F[E2, A]

  @inline final val unit: F[Nothing, Unit] = now(())
  @inline final def when[E](p: Boolean)(r: F[E, Unit]): F[E, Unit] = if (p) r else unit
}

object BIOApplicative {
  def apply[F[_, _]: BIOApplicative]: BIOApplicative[F] = implicitly
}

trait BIOGuarantee[F[+_, +_]] extends BIOApplicative[F]  {
  def guarantee[E, A](f: F[E, A])(cleanup: F[Nothing, Unit]): F[E, A]
}

object BIOGuarantee {
  def apply[F[_, _]: BIOGuarantee]: BIOGuarantee[F] = implicitly
}

trait BIOError[F[+_ ,+_]] extends BIOGuarantee[F] {
  def fail[E](v: => E): F[E, Nothing]
  def redeem[E, A, E2, B](r: F[E, A])(err: E => F[E2, B], succ: A => F[E2, B]): F[E2, B]

  @inline def redeemPure[E, A, B](r: F[E, A])(err: E => B, succ: A => B): F[Nothing, B] = redeem(r)(err.andThen(now), succ.andThen(now))
  @inline def attempt[E, A](r: F[E, A]): F[Nothing, Either[E, A]] = redeemPure(r)(Left(_), Right(_))
  @inline def catchAll[E, A, E2, A2 >: A](r: F[E, A])(f: E => F[E2, A2]): F[E2, A2] = redeem(r)(f, now)
  @inline def flip[E, A](r: F[E, A]) : F[A, E] = redeem(r)(now, fail(_))
  @inline final def fromOption[A](effect: => Option[A]): F[Unit, A] = fromOption(())(effect)

  def fromEither[E, V](effect: => Either[E, V]): F[E, V]
  def fromOption[E, A](errorOnNone: E)(effect: => Option[A]): F[E, A]
  def fromTry[A](effect: => Try[A]): F[Throwable, A]

  // defaults
  @inline override def bimap[E, A, E2, B](r: F[E, A])(f: E => E2, g: A => B): F[E2, B] = redeem(r)(e => fail(f(e)), a => now(g(a)))
}

object BIOError {
  def apply[F[_, _]: BIOError]: BIOError[F] = implicitly
}

trait BIOMonad[F[+_, +_]] extends BIOApplicative[F] {
  def flatMap[E, A, E2 >: E, B](r: F[E, A])(f: A => F[E2, B]): F[E2, B]
  def flatten[E, A](r: F[E, F[E, A]]): F[E, A] = flatMap(r)(identity)
  def traverse[E, A, B](l: Iterable[A])(f: A => F[E, B]): F[E, List[B]]

  @inline final def forever[E, A](r: F[E, A]): F[E, Nothing] = flatMap(r)(_ => forever(r))

  @inline final def traverse_[E, A, B](l: Iterable[A])(f: A => F[E, B]): F[E, Unit] = void(traverse(l)(f))
  @inline final def sequence[E, A, B](l: Iterable[F[E, A]]): F[E, List[A]] = traverse(l)(identity)
  @inline final def sequence_[E](l: Iterable[F[E, Unit]]): F[E, Unit] = void(traverse(l)(identity))

  // defaults
  @inline override def map[E, A, B](r: F[E, A])(f: A => B): F[E, B] = {
    flatMap(r)(a => now(f(a)))
  }

  /** execute two operations in order, return result of second operation */
  @inline override def *>[E, A, E2 >: E, B](f: F[E, A], next: => F[E2, B]): F[E2, B] = {
    flatMap[E, A, E2, B](f)(_ => next)
  }

  /** execute two operations in order, same as `*>`, but return result of first operation */
  @inline override def <*[E, A, E2 >: E, B](f: F[E, A], next: => F[E2, B]): F[E2, A] = {
    flatMap[E, A, E2, A](f)(a => map(next)(_ => a))
  }

  /** execute two operations in order, map their results */
  @inline override def map2[E, A, E2 >: E, B, C](r1: F[E, A], r2: => F[E2, B])(f: (A, B) => C): F[E2, C] = {
    flatMap[E, A, E2, C](r1)(a => map(r2)(b => f(a, b)))
  }
}

object BIOError {
  def apply[F[_, _]: BIOError]: BIOError[F] = implicitly
}

trait BIOBracket[F[+_, +_]] extends BIOError[F] with BIOMonad[F] {
  def bracketCase[E, A, B](acquire: F[E, A])(release: (A, BIOExit[E, B]) => F[Nothing, Unit])(use: A => F[E, B]): F[E, B]

  @inline def bracket[E, A, B](acquire: F[E, A])(release: A => F[Nothing, Unit])(use: A => F[E, B]): F[E, B] = {
    bracketCase[E, A, B](acquire)((a, _) => release(a))(use)
  }

  @inline def leftFlatMap[E, A, E2](r: F[E, A])(f: E => F[Nothing, E2]): F[E2, A] = {
    redeem(r)(e => flatMap(f(e))(fail(_)), now)
  }

  // defaults
  @inline override def guarantee[E, A](f: F[E, A])(cleanup: F[Nothing, Unit]): F[E, A] = {
    bracket(unit)(_ => cleanup)(_ => f)
  }
}

object BIOBracket {
  def apply[F[_, _]: BIOBracket]: BIOBracket[F] = implicitly
}

trait BIOPanic[F[+_, +_]] extends BIOBracket[F] {
  def terminate(v: => Throwable): F[Nothing, Nothing]

  @inline final def orTerminate[E <: Throwable, A](r: F[E, A]): F[Nothing, A] = catchAll(r)(terminate(_))

  def sandbox[E, A](r: F[E, A]): F[BIOExit.Failure[E], A]
  def sandboxWith[E, A, E2, B](r: F[E, A])(f: F[BIOExit.Failure[E], A] => F[BIOExit.Failure[E2], B]): F[E2, B]
}

object BIOPanic {
  def apply[F[_, _]: BIOPanic]: BIOPanic[F] = implicitly
}

trait BIO[F[+_, +_]] extends BIOPanic[F] {
  type Or[+E, +A] = F[E, A]
  type Just[+A] = F[Nothing, A]

  @inline def syncThrowable[A](effect: => A): F[Throwable, A]
  @inline def sync[A](effect: => A): F[Nothing, A]
  @inline final def apply[A](effect: => A): F[Throwable, A] = syncThrowable(effect)

  @deprecated("use .now for pure values, .sync for effects or delayed computations", "29.04.2019")
  @inline def point[A](v: => A): F[Nothing, A]

  @inline override def fromEither[E, A](effect: => Either[E, A]): F[E, A] = flatMap(sync(effect)) {
    case Left(e) => fail(e): F[E, A]
    case Right(v) => now(v): F[E, A]
  }

  @inline override def fromOption[E, A](errorOnNone: E)(effect: => Option[A]): F[E, A] = {
    flatMap(sync(effect))(e => fromEither(e.toRight(errorOnNone)))
  }

  @inline override def fromTry[A](effect: => Try[A]): F[Throwable, A] = {
    syncThrowable(effect.get)
  }
}

object BIO extends BIOSyntax {
  @inline def apply[F[+_, +_], A](effect: => A)(implicit BIO: BIO[F]): F[Throwable, A] = BIO.syncThrowable(effect)

  @inline def apply[F[+_, +_] : BIO]: BIO[F] = implicitly

  implicit object BIOZio extends BIOZio

  class BIOZio extends BIO[IO] with BIOExit.ZIO {

    @inline override final def now[A](a: A): IO[Nothing, A] = IO.succeed(a)
    @inline override final def sync[A](effect: => A): IO[Nothing, A] = IO.effectTotal(effect)
    @inline override final def point[R](v: => R): IO[Nothing, R] = IO.succeedLazy(v)
    @inline override final def syncThrowable[A](effect: => A): IO[Throwable, A] = IO.effect(effect)

    @inline override final def fail[E](v: => E): IO[E, Nothing] = IO.effectTotal(v).flatMap(IO.fail)
    @inline override final def terminate(v: => Throwable): IO[Nothing, Nothing] = IO.effectTotal(v).flatMap(IO.die)

    @inline override final def fromEither[L, R](v: => Either[L, R]): IO[L, R] = IO.fromEither(v)
    @inline override final def fromTry[A](effect: => Try[A]): IO[Throwable, A] = IO.fromTry(effect)

    @inline override final def void[E, A](r: IO[E, A]): IO[E, Unit] = r.unit
    @inline override final def map[E, A, B](r: IO[E, A])(f: A => B): IO[E, B] = r.map(f)

    @inline override final def leftMap[E, A, E2](r: IO[E, A])(f: E => E2): IO[E2, A] = r.mapError(f)
    @inline override final def leftFlatMap[E, A, E2](r: IO[E, A])(f: E => IO[Nothing, E2]): IO[E2, A] = r.flatMapError(f)
    @inline override final def flip[E, A](r: IO[E, A]): IO[A, E] = r.flip
    @inline override final def bimap[E, A, E2, B](r: IO[E, A])(f: E => E2, g: A => B): IO[E2, B] = r.bimap(f, g)

    @inline override final def flatMap[E, A, E1 >: E, B](r: IO[E, A])(f0: A => IO[E1, B]): IO[E1, B] = r.flatMap(f0)
    @inline override final def flatten[E, A](r: IO[E, IO[E, A]]): IO[E, A] = IO.flatten(r)
    @inline override final def *>[E, A, E2 >: E, B](f: IO[E, A], next: => IO[E2, B]): IO[E2, B] = f *> next
    @inline override final def <*[E, A, E2 >: E, B](f: IO[E, A], next: => IO[E2, B]): IO[E2, A] = f <* next
    @inline override final def map2[E, A, E2 >: E, B, C](r1: IO[E, A], r2: => IO[E2, B])(f: (A, B) => C): IO[E2, C] = r1.zipWith(IO.suspend(r2))(f)

    @inline override final def redeem[E, A, E2, B](r: IO[E, A])(err: E => IO[E2, B], succ: A => IO[E2, B]): IO[E2, B] = r.foldM(err, succ)
    @inline override final def catchAll[E, A, E2, A2 >: A](r: IO[E, A])(f: E => IO[E2, A2]): IO[E2, A2] = r.catchAll(f)
    @inline override final def guarantee[E, A](f: IO[E, A])(cleanup: IO[Nothing, Unit]): IO[E, A] = f.ensuring(cleanup)
    @inline override final def attempt[E, A](r: IO[E, A]): IO[Nothing, Either[E, A]] = r.either
    @inline override final def redeemPure[E, A, B](r: IO[E, A])(err: E => B, succ: A => B): IO[Nothing, B] = r.fold(err, succ)

    @inline override final def bracket[E, A, B](acquire: IO[E, A])(release: A => IO[Nothing, Unit])(use: A => IO[E, B]): IO[E, B] =
      IO.bracket(acquire)(v => release(v))(use)

    @inline override final def bracketCase[E, A, B](acquire: IO[E, A])(release: (A, BIOExit[E, B]) => IO[Nothing, Unit])(use: A => IO[E, B]): IO[E, B] =
      IO.bracketExit[Any, E, A, B](acquire, { case (a, exit) => release(a, toBIOExit(exit)) }, use)

    @inline override final def traverse[E, A, B](l: Iterable[A])(f: A => IO[E, B]): IO[E, List[B]] = IO.foreach(l)(f)

    @inline override final def sandboxWith[E, A, E2, B](r: IO[E, A])(f: IO[BIOExit.Failure[E], A] => IO[BIOExit.Failure[E2], B]): IO[E2, B] = {
      r.sandboxWith[Any, E2, B](r => f(r.mapError(toBIOExit[E])).mapError(failureToCause[E2]))
    }

    @inline override final def sandbox[E, A](r: IO[E, A]): IO[BIOExit.Failure[E], A] = r.sandbox.mapError(toBIOExit[E])
  }

}

