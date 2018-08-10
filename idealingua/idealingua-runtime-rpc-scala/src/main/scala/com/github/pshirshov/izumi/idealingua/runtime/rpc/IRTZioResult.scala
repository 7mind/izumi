package com.github.pshirshov.izumi.idealingua.runtime.rpc

import scalaz.zio.IO


trait IRTZioResult extends IRTResult[IO] {
  def choice[L, R](v: => Either[L, R]): Or[L, R] = IO.fromEither(v)

  def just[R](v: => R): Just[R] = IO.point(v)
}



