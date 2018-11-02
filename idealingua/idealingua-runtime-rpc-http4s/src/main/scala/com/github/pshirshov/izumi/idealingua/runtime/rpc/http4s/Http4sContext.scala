package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import _root_.io.circe._
import cats.effect.{ConcurrentEffect, Timer}
import com.github.pshirshov.izumi.functional.bio.{BIOAsync, BIORunner, CIORunner}
import com.github.pshirshov.izumi.logstage.api.IzLogger
import org.http4s._
import org.http4s.dsl._

import scala.concurrent.ExecutionContext
import scala.language.higherKinds


trait Http4sContext {
  type BiIO[+E, +V]

  type CatsIO[+T]

  type MaterializedStream = String

  type StreamDecoder = EntityDecoder[CatsIO, MaterializedStream]

  implicit def BIO: BIOAsync[BiIO]

  implicit def CIO: ConcurrentEffect[CatsIO]

  implicit def CIOT: Timer[CatsIO]

  def CIORunner: CIORunner[CatsIO]

  def BIORunner: BIORunner[BiIO]

  def dsl: Http4sDsl[CatsIO]

  def logger: IzLogger

  def printer: Printer

  def clientExecutionContext: ExecutionContext

  sealed trait Aux[_BiIO[+_, +_], _CatsIO[+_]] extends Http4sContext {
    override type BiIO[+E, +V] = _BiIO[E, V]

    override type CatsIO[+T] = _CatsIO[T]
  }

  sealed trait IMPL[C <: Http4sContext] extends Aux[C#BiIO, C#CatsIO] {

  }

  final type DECL = this.type

  def self: IMPL[DECL] = IMPL.apply[DECL]

  object IMPL {
    def apply[C <: Http4sContext]: IMPL[C] = new IMPL[C] {
      override def BIORunner: BIORunner[C#BiIO] = Http4sContext.this.BIORunner.asInstanceOf[BIORunner[C#BiIO]]

      override implicit def BIO: BIOAsync[C#BiIO] = Http4sContext.this.BIO.asInstanceOf[BIOAsync[C#BiIO]]

      override def CIORunner: CIORunner[C#CatsIO] = Http4sContext.this.CIORunner.asInstanceOf[CIORunner[C#CatsIO]]

      override def dsl: Http4sDsl[C#CatsIO] = Http4sContext.this.dsl.asInstanceOf[Http4sDsl[C#CatsIO]]

      override implicit def CIO: ConcurrentEffect[C#CatsIO] = Http4sContext.this.CIO.asInstanceOf[ConcurrentEffect[C#CatsIO]]

      override implicit def CIOT: Timer[C#CatsIO] = Http4sContext.this.CIOT.asInstanceOf[Timer[C#CatsIO]]


      override def logger: IzLogger = Http4sContext.this.logger

      override def printer: Printer = Http4sContext.this.printer

      override def clientExecutionContext: ExecutionContext = Http4sContext.this.clientExecutionContext
    }

  }

}
