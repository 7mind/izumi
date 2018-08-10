package com.github.pshirshov.izumi.idealingua.runtime.rpc

import scalaz.zio.IO

import scala.language.higherKinds

trait IRTResult {
    type Or[E, V]
    type Just[V]

    def choice[E, V](v: => Either[E, V]): Or[E, V]
    def just[V](v: => V): Just[V]
}

trait IRTZioResult extends IRTResult {
  type Or[E, V] = IO[E, V]
  type Just[V] = IO[Nothing, V]

  def choice[L, R](v: => Either[L, R]): Or[L, R] = {
    try {
      v match {
        case Right(r) =>
          IO.point(r)
        case Left(l) =>
          IO.fail(l)
      }
    } catch {
      case t: Throwable =>
        IO.terminate(t)
    }
  }

  def just[R](v: => R): Just[R] = {
    IO.point(v)
  }
}



