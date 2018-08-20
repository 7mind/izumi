package com.github.pshirshov.izumi.idealingua.runtime.rpc

import cats.{Monad, MonadError}
import cats.data.EitherT
import cats.effect.{ConcurrentEffect, Sync, Timer}
import com.github.pshirshov.izumi.idealingua.runtime.rpc.IRTResult.EitherTResult
import scalaz.zio.{ExitResult, IO, Retry}

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.language.higherKinds

trait IRTResult[R[_, _]] {
  type Or[E, V] = R[E, V]
  type Just[V] = R[Nothing, V]

  @inline def fromEither[E, V](v: => Either[E, V]): Or[E, V]

  @inline def point[E, V](v: => V): Or[E, V]

  @inline def now[E, A](a: A): Or[E, A]

  @inline def fail[E, A](v: => E): Or[E, A]

  @inline def terminate[E, V](v: => Throwable): Or[E, V]

  @inline def syncThrowable[A](effect: => A): Or[Throwable, A]

  @inline def sync[E, A](effect: => A): Or[E, A]

  @inline def maybe[E, V](v: => Either[Throwable, V]): Or[E, V] = {
    v match {
      case Left(f) =>
        terminate(f)
      case Right(r) =>
        point(r)
    }
  }

  @inline final def unit[E]: Or[E, Unit] = now(())

  @inline def bracket0[E, A, B](acquire: R[E, A])(release: A => R[Nothing, Unit])(use: A => R[E, B]): R[E, B]

  @inline def sleep[E](duration: FiniteDuration): Or[E, Unit]

  @inline def map[E, A, B](r: Or[E, A])(f: A => B): R[E, B]

  @inline def leftMap[E, A, E2](r: Or[E, A])(f: E => E2): R[E2, A]

  @inline def bimap[E, A, E2, B](r: Or[E, A])(f: E => E2, g: A => B): R[E2, B]

  @inline def flatMap[E, A, E1 >: E, B](r: Or[E, A])(f0: A => R[E1, B]): R[E1, B]

  @inline def redeem[E, A, E2, B](r: Or[E, A])(err: E => R[E2, B], succ: A => R[E2, B]): R[E2, B]

  @inline def sandboxWith[E, A, E2, B](r: Or[E, A])(f: R[Either[List[Throwable], E], A] => R[Either[List[Throwable], E2], B]): R[E2, B]

  @inline def retryOrElse[A, E, A2 >: A, E1 >: E, S, E2](r: Or[E, A])(duration: FiniteDuration, orElse: => R[E2, A2]): R[E2, A2]
}

trait IRTResultApi[T[K[_, _]] <: IRTResult[K]] {

  implicit class IRTResultApi[R[_, _] : T, E, A](val r: R[E, A]) {
    val R: T[R] = implicitly

    @inline def map[B](f: A => B): R[E, B] = R.map(r)(f)

    @inline def leftMap[E2](f: E => E2): R[E2, A] = R.leftMap(r)(f)

    @inline def bimap[E2, B](f: E => E2, g: A => B): R[E2, B] = R.bimap(r)(f, g)

    @inline def flatMap[E1 >: E, B](f0: A => R[E1, B]): R[E1, B] = R.flatMap[E, A, E1, B](r)(f0)

    @inline def redeem[E2, B](err: E => R[E2, B], succ: A => R[E2, B]): R[E2, B] = R.redeem[E, A, E2, B](r)(err, succ)

    @inline final def redeemPure[E2, B](err: E => B, succ: A => B): R[E2, B] =
      redeem(err.andThen(R.now), succ.andThen(R.now))

    @inline def sandboxWith[E2, B](f: R[Either[List[Throwable], E], A] => R[Either[List[Throwable], E2], B]): R[E2, B] = R.sandboxWith(r)(f)

    @inline def retryOrElse[A2 >: A, E1 >: E, S, E2](duration: FiniteDuration, orElse: => R[E2, A2]): R[E2, A2] = R.retryOrElse[A, E, A2, E1, S, E2](r)(duration, orElse)
  }

  def apply[R[_, _] : T, E, A](r: R[E, A]): IRTResultApi[R, E, A] = new IRTResultApi[R, E, A](r)
}

object IRTResult extends IRTResultApi[IRTResult] {

  implicit object IRTResultZio extends IRTResult[IO] {
    @inline override def bracket0[E, A, B](acquire: Or[E, A])(release: A => Or[Nothing, Unit])(use: A => Or[E, B]): Or[E, B] =
      IO.bracket0(acquire)((v, _: ExitResult[E, B]) => release(v))(use)

    @inline override def sleep[E](duration: FiniteDuration): Or[E, Unit] = IO.sleep(duration)

    @inline override def sync[E, A](effect: => A): Or[E, A] = IO.sync(effect)

    @inline override def now[E, A](a: A): Or[E, A] = IO.now(a)

    @inline override def syncThrowable[A](effect: => A): Or[Throwable, A] = IO.syncThrowable(effect)

    @inline override def fromEither[L, R](v: => Either[L, R]): Or[L, R] = IO.fromEither(v)

    @inline override def point[E, R](v: => R): Or[E, R] = IO.point(v)

    @inline override def terminate[E, R](v: => Throwable): Or[E, R] = IO.terminate(v)

    @inline override def fail[E, A](v: => E): Or[E, A] = IO.fail(v)

    @inline override def map[E, A, B](r: Or[E, A])(f: A => B): Or[E, B] = r.map(f)

    @inline override def leftMap[E, A, E2](r: Or[E, A])(f: E => E2): Or[E2, A] = r.leftMap(f)

    @inline override def bimap[E, A, E2, B](r: Or[E, A])(f: E => E2, g: A => B): Or[E2, B] = r.bimap(f, g)

    @inline override def flatMap[E, A, E1 >: E, B](r: Or[E, A])(f0: A => IO[E1, B]): Or[E1, B] = r.flatMap(f0)

    @inline override def redeem[E, A, E2, B](r: Or[E, A])(err: E => Or[E2, B], succ: A => Or[E2, B]): Or[E2, B] = r.redeem(err, succ)

    @inline override def sandboxWith[E, A, E2, B](r: Or[E, A])(f: Or[Either[List[Throwable], E], A] => Or[Either[List[Throwable], E2], B]): Or[E2, B] =
      r.sandboxWith(f)

    @inline override def retryOrElse[A, E, A2 >: A, E1 >: E, S, E2](r: Or[E, A])(duration: FiniteDuration, orElse: => Or[E2, A2]): Or[E2, A2] =
      r.retryOrElse(Retry.duration(duration), {
        (_: Any, _: Any) =>
          orElse
      })
  }
//
//    implicit def EitherTMonadResult[M[_]: ConcurrentEffect: Timer]: IRTResult[EitherT[M, ?, ?]] = new EitherTResultBase[M]
//
//    class EitherTResultBase[M[_]: ConcurrentEffect: Timer] extends IRTResult[EitherT[M, ?, ?]] {
//      import cats.implicits._
//      import cats.effect.implicits._
//
//      def ME[E](implicit me: MonadError[EitherT[M, E, ?], E]): MonadError[Or[E, ?], E] = me
//
//      case class Bracket0Exception[E](e: E) extends RuntimeException(s"Exception in EitherT bracket0: $e")
//
//      @inline override def terminate[E, V](v: => Throwable): Or[E, V] = EitherT(MonadError[M, Throwable].raiseError[Either[E, V]](v))
//
//      @inline override def point[E, V](v: => V): Or[E, V] = ME[E].pure(v)
//
//      @inline override def fail[E, A](v: => E): Or[E, A] = ME[E].raiseError(v)
//
//      @inline override def map[E, A, B](r: Or[E, A])(f: A => B): Or[E, B] = r.map(f)
//
//      @inline override def leftMap[E, A, E2](r: Or[E, A])(f: E => E2): Or[E2, A] = r.leftMap(f)
//
//      @inline override def bimap[E, A, E2, B](r: Or[E, A])(f: E => E2, g: A => B): Or[E2, B] = r.bimap(f, g)
//
//      @inline override def flatMap[E, A, E1 >: E, B](r: Or[E, A])(f0: A => EitherT[M, E1, B]): EitherT[M, E1, B] = r.flatMap(f0)
//
//      @inline override def fromEither[E, V](v: => Either[E, V]): Or[E, V] = EitherT.fromEither(v)
//
//      @inline override def syncThrowable[A](effect: => A): Or[Throwable, A] = ME[Throwable].catchNonFatal(effect)
//
//      @inline override def sync[E, A](effect: => A): Or[E, A] = EitherT(Sync[M].catchNonFatal(effect).map(Right(_)))
//
//      @inline override def bracket0[E, A, B](acquire: EitherT[M, E, A])(release: A => EitherT[M, Nothing, Unit])(use: A => EitherT[M, E, B]): EitherT[M, E, B] =
//        ConcurrentEffect[EitherT[M, Throwable, ?]]
//          .bracket {
//            acquire.leftMap[Throwable](Bracket0Exception(_))
//          } {
//            use.andThen(_.leftMap[Throwable](Bracket0Exception(_)))
//          } {
//            release.andThen(_.leftMap[Throwable](Bracket0Exception(_)))
//          }.leftFlatMap {
//          case Bracket0Exception(e: E @unchecked) =>
//            fail[E, B](e)
//          case e =>
//            terminate(e)
//        }
//
//      @inline override def sleep[E](duration: FiniteDuration): Or[E, Unit] = EitherT(Timer[M].sleep(duration).map(Right(_)))
//
//      @inline override def redeem[E, A, E2, B](r: Or[E, A])(err: E => EitherT[M, E2, B], succ: A => EitherT[M, E2, B]): EitherT[M, E2, B] =
//        EitherT[M, E2, B](r.value.map {
//          case Left(e) => err(e)
//          case Right(v) => succ(v)
//        }.map(Right[E2, B](_))).flatten
//
//      @inline override def sandboxWith[E, A, E2, B](r: Or[E, A])(f: EitherT[M, Either[List[Throwable], E], A] => EitherT[M, Either[List[Throwable], E2], B]): EitherT[M, E2, B] = ???
//        dunno
//
//      @inline override def retryOrElse[A, E, A2 >: A, E1 >: E, S, E2](r: Or[E, A])(duration: FiniteDuration, orElse: => EitherT[M, E2, A2]): EitherT[M, E2, A2] =
//        dunno
//    }
}





