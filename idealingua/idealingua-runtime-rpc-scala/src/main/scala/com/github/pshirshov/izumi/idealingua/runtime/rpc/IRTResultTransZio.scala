package com.github.pshirshov.izumi.idealingua.runtime.rpc

import cats.arrow.FunctionK
import scalaz.zio._

import scala.concurrent.duration._
import scala.language.higherKinds

trait IRTResultTransZio[R[+ _, + _]] extends IRTResult[R] {
  def toZio[E]: FunctionK[R[E, ?], IO[E, ?]]

  def ofZio[E]: FunctionK[IO[E, ?], R[E, ?]]
}

object IRTResultTransZio extends IRTResultApi[IRTResultTransZio] {

  implicit object IRTResultZio extends IRTResultTransZio[IO] {
    @inline def bracket0[E, A, B](acquire: Or[E, A])(release: A => Or[Nothing, Unit])(use: A => Or[E, B]): Or[E, B] =
      IO.bracket0(acquire)((v, _: ExitResult[E, B]) => release(v))(use)

    @inline def sleep(duration: Duration): IRTResultZio.Or[Nothing, Unit] = IO.sleep(duration)

    @inline def sync[A](effect: => A): Or[Nothing, A] = IO.sync(effect)

    @inline def now[A](a: A): Just[A] = IO.now(a)

    @inline def syncThrowable[A](effect: => A): Or[Throwable, A] = IO.syncThrowable(effect)

    @inline def fromEither[L, R](v: => Either[L, R]): Or[L, R] = IO.fromEither(v)

    @inline def point[R](v: => R): Just[R] = IO.point(v)

    @inline def terminate[R](v: => Throwable): Just[R] = IO.terminate(v)

    @inline def fail[E](v: => E): Or[E, Nothing] = IO.fail(v)

    @inline def map[E, A, B](r: Or[E, A])(f: A => B): Or[E, B] = r.map(f)

    @inline def leftMap[E, A, E2](r: Or[E, A])(f: E => E2): Or[E2, A] = r.leftMap(f)

    @inline def bimap[E, A, E2, B](r: Or[E, A])(f: E => E2, g: A => B): Or[E2, B] = r.bimap(f, g)

    @inline def flatMap[E, A, E1 >: E, B](r: Or[E, A])(f0: A => IO[E1, B]): Or[E1, B] = r.flatMap(f0)

    @inline def redeem[E, A, E2, B](r: Or[E, A])(err: E => Or[E2, B], succ: A => Or[E2, B]): Or[E2, B] = r.redeem(err, succ)

    @inline def sandboxWith[E, A, E2, B](r: Or[E, A])(f: Or[Either[List[Throwable], E], A] => Or[Either[List[Throwable], E2], B]): Or[E2, B] =
      r.sandboxWith(f)

    @inline def retryOrElse[A, E, A2 >: A, E1 >: E, S, E2](r: Or[E, A])(duration: FiniteDuration, orElse: => Or[E2, A2]): Or[E2, A2] =
      r.retryOrElse(Retry.duration(duration), {
        (_: Any, _: Any) =>
          orElse
      })

    @inline def toZio[E]: FunctionK[IO[E, ?], IO[E, ?]] = FunctionK.id

    @inline def ofZio[E]: FunctionK[IO[E, ?], IO[E, ?]] = FunctionK.id
  }


  //type xyz[λ[(-[A], +[B]) => Function2[A, Int, B]]]

  //type xyz[λ[(`E[+_]`, `A[+_]`) => EitherT[cats.effect.IO, E, A]]]
  //  @deprecated("BIO<->EitherT adapter is not recommended to use", "")
  //  implicit object EitherTResult extends IRTResult[λ[(`+E`, `+A`) => EitherT[cats.effect.IO, E, A]]] {
  //    def ME[E]: MonadError[Or[E, ?], E] = implicitly
  //
  //    // this isn't nice
  //    @inline def cancel[V](v: => Throwable): Just[V] = ME[Nothing].point(throw v)
  //
  //    @inline def choice[E, V](v: => Either[E, V]): Or[E, V] = v match {
  //      case Right(r) =>
  //        ME[E].pure(r)
  //
  //      case Left(l) =>
  //        ME[E].raiseError(l)
  //    }
  //
  //    @inline def just[V](v: => V): Just[V] = ME[Nothing].pure(v)
  //
  //    @inline def stop[E](v: => E): EitherTResult.Or[E, Nothing] = ME[E].raiseError(v)
  //
  //    @inline def map[E, A, B](r: EitherTResult.Or[E, A])(f: A => B): EitherTResult.Or[E, B] = r.map(f)
  //
  //    @inline def leftMap[E, A, E2](r: EitherTResult.Or[E, A])(f: E => E2): EitherTResult.Or[E2, A] = r.leftMap(f)
  //
  //    @inline def bimap[E, A, E2, B](r: EitherTResult.Or[E, A])(f: E => E2, g: A => B): EitherTResult.Or[E2, B] = r.bimap(f, g)
  //
  //    @inline def flatMap[E, A, E1 >: E, B](r: EitherTResult.Or[E, A])(f0: A => EitherT[effect.IO, E1, B]): EitherT[effect.IO, E1, B] = r.flatMap(f0)
  //  }
}
