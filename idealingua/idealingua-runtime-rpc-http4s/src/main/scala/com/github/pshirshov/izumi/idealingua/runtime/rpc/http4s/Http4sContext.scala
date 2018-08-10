package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import cats.effect
import com.github.pshirshov.izumi.logstage.api.IzLogger
import org.http4s._
import org.http4s.dsl._
import scalaz.zio.{IO, RTS}


object ZIOR extends RTS {
  override def defaultHandler: List[Throwable] => IO[Nothing, Unit] = _ => IO.sync(())
}

trait Http4sContext {
  type CIO[T] = cats.effect.IO[T]
  val CIO: effect.IO.type = cats.effect.IO
  type ZIO[E, V] = scalaz.zio.IO[E, V]
  val ZIO: IO.type = scalaz.zio.IO

  type MaterializedStream = String
  type StreamDecoder = EntityDecoder[CIO, MaterializedStream]

  protected def dsl: Http4sDsl[CIO]

  protected def logger: IzLogger


}
