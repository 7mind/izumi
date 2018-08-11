package com.github.pshirshov.izumi.idealingua.runtime.rpc

import cats.arrow.FunctionK
import scalaz.zio.IO

import scala.language.higherKinds

trait IRTResultTransZio[R[_, _]] extends IRTResult[R] {
  def toZio[E]: FunctionK[R[E, ?], IO[E, ?]]
}


trait IRTZioResult extends IRTResultTransZio[IO] {
  @inline def choice[L, R](v: => Either[L, R]): Or[L, R] = IO.fromEither(v)

  @inline def just[R](v: => R): Just[R] = IO.point(v)

  @inline def stop[R](v: => Throwable): Just[R] = IO.terminate(v)

  override def toZio[E]: FunctionK[IO[E, ?], IO[E, ?]] = FunctionK.id
}



