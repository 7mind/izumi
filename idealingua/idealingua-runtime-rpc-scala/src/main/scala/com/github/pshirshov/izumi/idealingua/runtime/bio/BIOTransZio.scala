package com.github.pshirshov.izumi.idealingua.runtime.bio

import cats.arrow.FunctionK
import scalaz.zio._

import scala.language.higherKinds

trait BIOTransZio[R[_, _]] {
  def toZio[E]: FunctionK[R[E, ?], IO[E, ?]]

  def ofZio[E]: FunctionK[IO[E, ?], R[E, ?]]
}

object BIOTransZio {
  def apply[R[+_, +_]: BIOTransZio]: BIOTransZio[R] = implicitly

  implicit object IdTransZio extends BIOTransZio[IO]{
    @inline def toZio[E]: FunctionK[IO[E, ?], IO[E, ?]] = FunctionK.id

    @inline def ofZio[E]: FunctionK[IO[E, ?], IO[E, ?]] = FunctionK.id
  }

}
