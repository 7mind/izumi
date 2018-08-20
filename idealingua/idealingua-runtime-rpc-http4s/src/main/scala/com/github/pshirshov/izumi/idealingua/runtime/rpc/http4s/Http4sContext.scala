package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import _root_.io.circe._
import cats.effect.{ConcurrentEffect, Timer}
import com.github.pshirshov.izumi.idealingua.runtime.rpc.IRTResultTransZio
import com.github.pshirshov.izumi.logstage.api.IzLogger
import org.http4s._
import org.http4s.dsl._

import scala.language.higherKinds


trait Http4sContext {
  type BIO[+E, +V]

  type CIO[+T]

  type MaterializedStream = String

  type StreamDecoder = EntityDecoder[CIO, MaterializedStream]

  protected implicit def BIO: IRTResultTransZio[BIO]

  protected implicit def CIO: ConcurrentEffect[CIO]

  protected implicit def CIOT: Timer[CIO]

  protected def CIORunner: CIORunner[CIO]

  protected def dsl: Http4sDsl[CIO]

  protected def logger: IzLogger

  protected def printer: Printer
}
