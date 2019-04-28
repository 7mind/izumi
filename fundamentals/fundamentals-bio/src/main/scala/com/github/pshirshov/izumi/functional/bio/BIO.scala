package com.github.pshirshov.izumi.functional.bio

import scalaz.zio.IO

trait BIO[R[+ _, + _]] extends BIOInvariant[R] {
  override type Or[+E, +V] = R[E, V]
  override type Just[+V] = R[Nothing, V]

  @inline override def now[A](a: A): R[Nothing, A]

  @inline override def syncThrowable[A](effect: => A): R[Throwable, A]

  @inline override def sync[A](effect: => A): R[Nothing, A]

  @inline override def point[V](v: => V): R[Nothing, V]

  @inline override def fail[E](v: => E): R[E, Nothing]

  @inline override def terminate(v: => Throwable): R[Nothing, Nothing]

  @inline override def map[E, A, B](r: R[E, A])(f: A => B): R[E, B] = flatMap(r)(a => now(f(a)))

  @inline override def flatMap[E, A, E1 >: E, B](r: R[E, A])(f0: A => R[E1, B]): R[E1, B]

  @inline override def redeem[E, A, E2, B](r: R[E, A])(err: E => R[E2, B], succ: A => R[E2, B]): R[E2, B]

  @inline override def bracketCase[E, A, B](acquire: R[E, A])(release: (A, BIOExit[E, B]) => R[Nothing, Unit])(use: A => R[E, B]): R[E, B]

  @inline override def sandboxWith[E, A, E2, B](r: R[E, A])(f: R[BIOExit.Failure[E], A] => R[BIOExit.Failure[E2], B]): R[E2, B]

  @inline override def sandbox[E, A](r: R[E, A]): R[BIOExit.Failure[E], A]

  @inline override final val unit: R[Nothing, Unit] = now(())

  @inline override def void[E, A](r: R[E, A]): R[E, Unit] = map(r)(_ => ())

  @inline override def leftMap[E, A, E2](r: R[E, A])(f: E => E2): R[E2, A] = redeem[E, A, E2, A](r)(e => fail(f(e)), now)

  @inline override def bimap[E, A, E2, B](r: R[E, A])(f: E => E2, g: A => B): R[E2, B] = redeem(r)(e => fail(f(e)), a => point(g(a)))

  @inline override def fromEither[E, V](effect: => Either[E, V]): R[E, V] = flatMap(sync(effect)) {
     case Left(e) => fail(e): R[E, V]
     case Right(v) => now(v): R[E, V]
   }

  @inline override def bracket[E, A, B](acquire: R[E, A])(release: A => R[Nothing, Unit])(use: A => R[E, B]): R[E, B] = {
    bracketCase[E, A, B](acquire)((a, _) => release(a))(use)
  }

  @inline override def leftFlatMap[E, A, E2](r: R[E, A])(f: E => R[Nothing, E2]): R[E2, A] = redeem(r)(e => flatMap(f(e))(fail(_)), now)

  @inline override def flip[E, A](r: R[E, A]) : R[A, E] = redeem(r)(now, fail(_))

  @inline override final def widen[E, A, E1 >: E, A1 >: A](r: R[E, A]): R[E1, A1] = r

  @inline override final def forever[E1, A1](r: R[E1, A1]): R[E1, Nothing] = flatMap(r)(_ => forever(r))

}

object BIO extends BIOSyntax {
  @inline def apply[F[+_, +_], A](effect: => A)(implicit BIO: BIO[F]): F[Throwable, A] = BIO.syncThrowable(effect)

  @inline def apply[R[+ _, + _] : BIO]: BIO[R] = implicitly

  implicit object BIOZio extends BIOZio

  class BIOZio extends BIO[IO] with BIOExit.ZIO {

    @inline override def bracket[E, A, B](acquire: IO[E, A])(release: A => IO[Nothing, Unit])(use: A => IO[E, B]): IO[E, B] =
      IO.bracket(acquire)(v => release(v))(use)

    @inline override def bracketCase[E, A, B](acquire: IO[E, A])(release: (A, BIOExit[E, B]) => IO[Nothing, Unit])(use: A => IO[E, B]): IO[E, B] =
      IO.bracketExit[Any, E, A, B](acquire, { case (a, exit) => release(a, toBIOExit(exit)) }, use)

    @inline override def sync[A](effect: => A): IO[Nothing, A] = IO.effectTotal(effect)

    @inline override def now[A](a: A): IO[Nothing, A] = IO.succeed(a)

    @inline override def syncThrowable[A](effect: => A): IO[Throwable, A] = IO.effect(effect)

    @inline override def fromEither[L, R](v: => Either[L, R]): IO[L, R] = IO.effectTotal(v).flatMap(IO.fromEither(_))

    @inline override def point[R](v: => R): IO[Nothing, R] = IO.succeedLazy(v)

    @inline override def void[E, A](r: IO[E, A]): IO[E, Unit] = r.unit

    @inline override def terminate(v: => Throwable): IO[Nothing, Nothing] = IO.effectTotal(v).flatMap(IO.die)

    @inline override def fail[E](v: => E): IO[E, Nothing] = IO.effectTotal(v).flatMap(IO.fail)

    @inline override def map[E, A, B](r: IO[E, A])(f: A => B): IO[E, B] = r.map(f)

    @inline override def leftMap[E, A, E2](r: IO[E, A])(f: E => E2): IO[E2, A] = r.mapError(f)

    @inline override def leftFlatMap[E, A, E2](r: IO[E, A])(f: E => IO[Nothing, E2]): IO[E2, A] = r.flatMapError(f)

    @inline override def flip[E, A](r: IO[E, A]): IO[A, E] = r.flip

    @inline override def bimap[E, A, E2, B](r: IO[E, A])(f: E => E2, g: A => B): IO[E2, B] = r.bimap(f, g)

    @inline override def flatMap[E, A, E1 >: E, B](r: IO[E, A])(f0: A => IO[E1, B]): IO[E1, B] = r.flatMap(f0)

    @inline override def redeem[E, A, E2, B](r: IO[E, A])(err: E => IO[E2, B], succ: A => IO[E2, B]): IO[E2, B] = r.foldM(err, succ)

    @inline override def traverse[E, A, B](l: Iterable[A])(f: A => IO[E, B]): IO[E, List[B]] = IO.foreach(l)(f)

    @inline override def sandboxWith[E, A, E2, B](r: IO[E, A])(f: IO[BIOExit.Failure[E], A] => IO[BIOExit.Failure[E2], B]): IO[E2, B] = {
      r.sandboxWith[Any, E2, B](r => f(r.mapError(toBIOExit[E])).mapError(failureToCause[E2]))
    }

    @inline override def sandbox[E, A](r: IO[E, A]): IO[BIOExit.Failure[E], A] = r.sandbox.mapError(toBIOExit[E])
  }

}

