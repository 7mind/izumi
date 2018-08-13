package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import scalaz.zio.{IO, RTS}

object ZIOR extends RTS {
  override def defaultHandler: List[Throwable] => IO[Nothing, Unit] = _ => IO.sync(())
}
