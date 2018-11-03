package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import cats.effect.{ConcurrentEffect, Timer}
import com.github.pshirshov.izumi.functional.bio.{BIOAsync, BIORunner, CIORunner}
import org.http4s._
import org.http4s.dsl._

import scala.concurrent.ExecutionContext
import scala.language.higherKinds


trait Http4sContext {
  type BiIO[+E, +V]

  type CatsIO[+T]

  type MaterializedStream = String

  type RequestContext

  type ClientContext

  type ClientId

  type StreamDecoder = EntityDecoder[CatsIO, MaterializedStream]

  implicit def BIO: BIOAsync[BiIO]

  implicit def CIO: ConcurrentEffect[CatsIO]

  implicit def CIOT: Timer[CatsIO]

  def CIORunner: CIORunner[CatsIO]

  def BIORunner: BIORunner[BiIO]

  def dsl: Http4sDsl[CatsIO]

  def clientExecutionContext: ExecutionContext

  final type DECL = this.type

  def self: IMPL[DECL] = IMPL.apply[DECL]

  sealed trait Aux[_BiIO[+_, +_], _CatsIO[+_], _RequestContext, _ClientId, _ClientContext] extends Http4sContext {
    override final type BiIO[+E, +V] = _BiIO[E, V]

    override final type CatsIO[+T] = _CatsIO[T]

    override final type RequestContext = _RequestContext
    override final type ClientContext = _ClientContext
    override final type ClientId = _ClientId
  }

  final class IMPL[C <: Http4sContext] extends Aux[C#BiIO, C#CatsIO, C#RequestContext, C#ClientId, C#ClientContext] {
    override def BIORunner: BIORunner[C#BiIO] = Http4sContext.this.BIORunner.asInstanceOf[BIORunner[C#BiIO]]

    override implicit def BIO: BIOAsync[C#BiIO] = Http4sContext.this.BIO.asInstanceOf[BIOAsync[C#BiIO]]

    override def CIORunner: CIORunner[C#CatsIO] = Http4sContext.this.CIORunner.asInstanceOf[CIORunner[C#CatsIO]]

    override def dsl: Http4sDsl[C#CatsIO] = Http4sContext.this.dsl.asInstanceOf[Http4sDsl[C#CatsIO]]

    override implicit def CIO: ConcurrentEffect[C#CatsIO] = Http4sContext.this.CIO.asInstanceOf[ConcurrentEffect[C#CatsIO]]

    override implicit def CIOT: Timer[C#CatsIO] = Http4sContext.this.CIOT.asInstanceOf[Timer[C#CatsIO]]

    override def clientExecutionContext: ExecutionContext = Http4sContext.this.clientExecutionContext
  }

  object IMPL {
    def apply[C <: Http4sContext]: IMPL[C] = new IMPL[C]
  }

}
