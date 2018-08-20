package com.github.pshirshov.izumi.idealingua.runtime.rpc


import cats.arrow.FunctionK
import scalaz.zio.IO

import scala.language.higherKinds

trait IRTResult[R[_, _]] {
  type Or[E, V] = R[E, V]
  type Just[V] = R[Nothing, V]

  @inline def choice[E, V](v: => Either[E, V]): Or[E, V]

  @inline def just[V](v: => V): Just[V]

  @inline def stop[V](v: => Throwable): Just[V]

  @inline def maybe[V](v: => Either[Throwable, V]): Just[V] = {
    v match {
      case Left(f) =>
        stop(f)
      case Right(r) =>
        just(r)
    }
  }
}

trait IRTResultTransZio[R[_, _]] extends IRTResult[R] {
  def toZio[E]: FunctionK[R[E, ?], IO[E, ?]]
  def fromZio[E]: FunctionK[IO[E, ?], R[E, ?]]
}

object IRTResultTransZio {

  implicit object IRTResultZio extends IRTResultTransZio[IO] {
    @inline def choice[L, R](v: => Either[L, R]): Or[L, R] = IO.fromEither(v)

    @inline def just[R](v: => R): Just[R] = IO.point(v)

    @inline def stop[R](v: => Throwable): Just[R] = IO.terminate(v)

    override def toZio[E]: FunctionK[IO[E, ?], IO[E, ?]] = FunctionK.id
    override def fromZio[E]: FunctionK[IO[E, ?], IO[E, ?]] = FunctionK.id
  }
}

