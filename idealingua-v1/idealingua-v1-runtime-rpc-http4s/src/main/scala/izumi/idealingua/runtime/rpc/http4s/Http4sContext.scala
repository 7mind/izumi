package izumi.idealingua.runtime.rpc.http4s

import cats.effect.{ConcurrentEffect, Timer}
import izumi.functional.bio.{BIOTemporal, BIORunner}
import org.http4s._
import org.http4s.dsl._

import scala.concurrent.ExecutionContext

trait Http4sContext { outer =>
  type BiIO[+E, +A]
  final type MonoIO[+A] = BiIO[Throwable, A]

  type MaterializedStream = String

  type RequestContext

  type MethodContext

  type ClientContext

  type ClientMethodContext

  type ClientId

  type StreamDecoder = EntityDecoder[MonoIO, MaterializedStream]

  implicit def F: BIOTemporal[BiIO]
  implicit def CIO: ConcurrentEffect[MonoIO]
  implicit def CIOT: Timer[MonoIO]

  def BIORunner: BIORunner[BiIO]
  val dsl: Http4sDsl[MonoIO]

  def clientExecutionContext: ExecutionContext

  final type DECL = this.type

  final def self: IMPL[DECL] = this

  type Aux[_BiIO[+_, +_], _RequestContext, _MethodContext, _ClientId, _ClientContext, _ClientMethodContext] = Http4sContext {
    type BiIO[+E, +V] = _BiIO[E, V]
    type RequestContext = _RequestContext
    type MethodContext = _MethodContext
    type ClientContext = _ClientContext
    type ClientMethodContext = _ClientMethodContext
    type ClientId = _ClientId
  }

  /**
    * This is to prove type equality between a type `C <: Http4sContext` and `c: C`
    * Scalac treats them as different, even when there's a Singleton upper bound!
    *
    * @see details: https://gist.github.com/pshirshov/1273add00d902a6cfebd72426d7ed758
    * @see dotty: https://github.com/lampepfl/dotty/issues/4583#issuecomment-435382992
    * @see intellij highlighting fixed: https://youtrack.jetbrains.net/issue/SCL-14533
    */
  final type IMPL[C <: Http4sContext] = Aux[C#BiIO, C#RequestContext, C#MethodContext, C#ClientId, C#ClientContext, C#ClientMethodContext]
}
