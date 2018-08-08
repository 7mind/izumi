package com.github.pshirshov.izumi.idealingua.runtime.cats

import cats.MonadError
import cats.effect.IO
import com.github.pshirshov.izumi.idealingua.runtime.rpc.IRTResult

trait RuntimeCats {
  implicit object IOResult extends IRTResult[IO] {
    private val M: MonadError[IO, Throwable] = implicitly[MonadError[IO, Throwable]]

    override def map[A, B](r: IO[A])(f: A => B): IO[B] = M.map(r)(f)

    override def flatMap[A, B](r: IO[A])(f: A => IO[B]): IO[B] = M.flatMap(r)(f)

    override def wrap[A](v: => A): IO[A] = M.pure(v)

    override def raiseError[A](e: Throwable): IO[A] = M.raiseError(e)
  }

}

object RuntimeCats extends RuntimeCats {

}
