package com.github.pshirshov.izumi.idealingua.runtime.cats

import cats.Monad
import cats.effect.IO
import com.github.pshirshov.izumi.idealingua.runtime.rpc.IRTResult

trait RuntimeCats {
  implicit object IOResult extends IRTResult[IO] {
    override def map[A, B](r: IO[A])(f: A => B): IO[B] = implicitly[Monad[IO]].map(r)(f)

    override def flatMap[A, B](r: IO[A])(f: A => IO[B]): IO[B] = implicitly[Monad[IO]].flatMap(r)(f)

    override def wrap[A](v: => A): IO[A] = implicitly[Monad[IO]].pure(v)
  }

}

object RuntimeCats extends RuntimeCats {

}
