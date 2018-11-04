package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import cats.effect.{ConcurrentEffect, Timer}
import com.github.pshirshov.izumi.functional.bio.{BIOAsync, BIORunner, CIORunner}
import org.http4s._
import org.http4s.dsl._

import scala.concurrent.ExecutionContext

trait Http4sContext { outer =>
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

  final def self: IMPL[DECL] = new IMPL[DECL]

  sealed trait Aux[_BiIO[+_, +_], _CatsIO[+_], _RequestContext, _ClientId, _ClientContext] extends Http4sContext {
    override final type BiIO[+E, +V] = _BiIO[E, V]

    override final type CatsIO[+T] = _CatsIO[T]

    override final type RequestContext = _RequestContext
    override final type ClientContext = _ClientContext
    override final type ClientId = _ClientId
  }

  // Mainly here for highlighting in Intellij, type alias works too
  final class IMPL[C <: Http4sContext] extends Aux[C#BiIO, C#CatsIO, C#RequestContext, C#ClientId, C#ClientContext] {
    override val BIORunner: BIORunner[C#BiIO] = outer.BIORunner.asInstanceOf[BIORunner[C#BiIO]]

    override implicit val BIO: BIOAsync[C#BiIO] = outer.BIO.asInstanceOf[BIOAsync[C#BiIO]]

    override val CIORunner: CIORunner[C#CatsIO] = outer.CIORunner.asInstanceOf[CIORunner[C#CatsIO]]

    override val dsl: Http4sDsl[C#CatsIO] = outer.dsl.asInstanceOf[Http4sDsl[C#CatsIO]]

    override implicit val CIO: ConcurrentEffect[C#CatsIO] = outer.CIO.asInstanceOf[ConcurrentEffect[C#CatsIO]]

    override implicit val CIOT: Timer[C#CatsIO] = outer.CIOT.asInstanceOf[Timer[C#CatsIO]]

    override val clientExecutionContext: ExecutionContext = outer.clientExecutionContext
  }
}
