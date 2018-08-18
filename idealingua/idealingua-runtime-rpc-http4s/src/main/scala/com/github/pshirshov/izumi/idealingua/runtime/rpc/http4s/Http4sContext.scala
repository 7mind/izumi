package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import cats.effect
import com.github.pshirshov.izumi.logstage.api.IzLogger
import org.http4s._
import org.http4s.dsl._
import scalaz.zio.IO
import _root_.io.circe._
import _root_.io.circe.syntax._

import scala.language.higherKinds


trait Http4sContext {
  type CIO[T] = cats.effect.IO[T]
  val CIO: effect.IO.type = cats.effect.IO
  type ZIO[E, V] = scalaz.zio.IO[E, V]
  val ZIO: IO.type = scalaz.zio.IO

  type BIO[E, V]

  type MaterializedStream = String
  type StreamDecoder = EntityDecoder[CIO, MaterializedStream]

  protected def dsl: Http4sDsl[CIO]

  protected def logger: IzLogger

  protected def encode(json: Json): String = {
    json.pretty(Printer.noSpaces.copy(dropNullValues = true))
  }

}
