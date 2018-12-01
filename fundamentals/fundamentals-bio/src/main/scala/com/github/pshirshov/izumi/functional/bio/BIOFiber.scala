package com.github.pshirshov.izumi.functional.bio


import scalaz.zio.{Fiber, IO}

import scala.util.Try

trait BIOFiber[F[_, _], E, A] {
  def join: F[E, A]

  def observe: F[Nothing, Try[Either[E, A]]]

  def interrupt: F[Nothing, Try[Either[E, A]]]
}

object BIOFiber {
  def fromZIO[E, A](f: Fiber[E, A]): BIOFiber[IO, E, A] =
    new BIOFiber[IO, E, A] {
      override val join: IO[E, A] = f.join
      override val observe: IO[Nothing, Try[Either[E, A]]] = f.observe.map(BIO.BIOZio.toTry)
      override def interrupt: IO[Nothing, Try[Either[E, A]]] = f.interrupt.map(BIO.BIOZio.toTry)
    }
}
