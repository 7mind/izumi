package com.github.pshirshov.izumi.idealingua.runtime.rpc

import cats.arrow.FunctionK
import scalaz.zio.IO

import scala.language.higherKinds

trait IRTZioableResult[R[_, _]] extends IRTResult[R] {
  implicit def conv[E]: FunctionK[R[E, ?], IO[E, ?]]
}


trait IRTZioResult extends IRTZioableResult[IO] {
  def choice[L, R](v: => Either[L, R]): Or[L, R] = IO.fromEither(v)

  def just[R](v: => R): Just[R] = IO.point(v)

  override implicit def conv[E]: FunctionK[IO[E, ?], IO[E, ?]] = FunctionK.id
}



