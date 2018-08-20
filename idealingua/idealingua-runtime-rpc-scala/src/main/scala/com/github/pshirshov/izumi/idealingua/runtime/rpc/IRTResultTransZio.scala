package com.github.pshirshov.izumi.idealingua.runtime.rpc

import cats.arrow.FunctionK
import scalaz.zio._

import scala.language.higherKinds

trait IRTResultTransZio[R[_, _]] {
  def toZio[E]: FunctionK[R[E, ?], IO[E, ?]]

  def ofZio[E]: FunctionK[IO[E, ?], R[E, ?]]
}

object IRTResultTransZio {

  implicit object IRTResultTransZio extends IRTResultTransZio[IO]{
    @inline def toZio[E]: FunctionK[IO[E, ?], IO[E, ?]] = FunctionK.id

    @inline def ofZio[E]: FunctionK[IO[E, ?], IO[E, ?]] = FunctionK.id
  }
}
