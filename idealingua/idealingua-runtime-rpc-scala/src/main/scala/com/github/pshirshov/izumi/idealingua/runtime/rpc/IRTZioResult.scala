package com.github.pshirshov.izumi.idealingua.runtime.rpc

import scalaz.zio.IO

import scala.language.higherKinds


trait IRTZioResult extends IRTResult {
  type Or[E, V] = IO[E, V]
  type Just[V] = IO[Nothing, V]

  def choice[L, R](v: => Either[L, R]): Or[L, R] = IO.fromEither(v)

  def just[R](v: => R): Just[R] = IO.point(v)
}



