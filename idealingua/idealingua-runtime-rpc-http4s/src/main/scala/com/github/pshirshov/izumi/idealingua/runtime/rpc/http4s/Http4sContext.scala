package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import _root_.io.circe._
import cats.effect.{ConcurrentEffect, Timer}
import com.github.pshirshov.izumi.idealingua.runtime.bio.BIOAsync
import com.github.pshirshov.izumi.logstage.api.IzLogger
import org.http4s._
import org.http4s.dsl._

import scala.language.higherKinds


trait Http4sContext {
  type BiIO[+E, +V]

  type CatsIO[+T]

  type MaterializedStream = String

  type StreamDecoder = EntityDecoder[CatsIO, MaterializedStream]

  protected implicit def BIO: BIOAsync[BiIO]

  protected implicit def CIO: ConcurrentEffect[CatsIO]

  protected implicit def CIOT: Timer[CatsIO]

  protected def CIORunner: CIORunner[CatsIO]

  protected def BIORunner: BIORunner[BiIO]

  protected def dsl: Http4sDsl[CatsIO]

  protected def logger: IzLogger

  protected def printer: Printer
}
