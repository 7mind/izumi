package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import cats.effect.{ConcurrentEffect, Timer}
import com.github.pshirshov.izumi.functional.bio.{BIOAsync, BIORunner}
import org.http4s._
import org.http4s.dsl._

import scala.concurrent.ExecutionContext

trait Http4sContext { outer =>
  type BiIO[+E, +A]
  final type MonoIO[+A] = BiIO[Throwable, A]

  type MaterializedStream

  type RequestContext

  type MethodContext

  type ClientContext

  type ClientMethodContext

  type ClientId

  type StreamDecoder = EntityDecoder[MonoIO, MaterializedStream]

  implicit def F: BIOAsync[BiIO]
  implicit def CIO: ConcurrentEffect[MonoIO]
  implicit def CIOT: Timer[MonoIO]

  def BIORunner: BIORunner[BiIO]
  def dsl: Http4sDsl[MonoIO]

  def clientExecutionContext: ExecutionContext

  final type DECL = this.type

  final def self: IMPL[DECL] = new IMPL[DECL]

  sealed trait Aux[_BiIO[+_, +_], _RequestContext, _MethodContext, _ClientId, _ClientContext, _ClientMethodContext] extends Http4sContext {
    override final type BiIO[+E, +V] = _BiIO[E, V]

    override final type MaterializedStream = String

    override final type RequestContext = _RequestContext
    override final type MethodContext = _MethodContext
    override final type ClientContext = _ClientContext
    override final type ClientMethodContext = _ClientMethodContext
    override final type ClientId = _ClientId
  }

  /**
    * Mainly here for highlighting in Intellij, type alias works too
    *
    * @see https://youtrack.jetbrains.net/issue/SCL-14533
    */
  final class IMPL[C <: Http4sContext] private[Http4sContext] extends Aux[C#BiIO, C#RequestContext, C#MethodContext, C#ClientId, C#ClientContext, C#ClientMethodContext] {
    override val BIORunner: BIORunner[C#BiIO] = outer.BIORunner.asInstanceOf[BIORunner[C#BiIO]]

    override implicit val F: BIOAsync[C#BiIO] = outer.F.asInstanceOf[BIOAsync[C#BiIO]]

    override val dsl: Http4sDsl[C#MonoIO] = outer.dsl.asInstanceOf[Http4sDsl[C#MonoIO]]

    override implicit val CIO: ConcurrentEffect[C#MonoIO] = outer.CIO.asInstanceOf[ConcurrentEffect[C#MonoIO]]

    override implicit val CIOT: Timer[C#MonoIO] = outer.CIOT.asInstanceOf[Timer[C#MonoIO]]

    override val clientExecutionContext: ExecutionContext = outer.clientExecutionContext
  }
}
