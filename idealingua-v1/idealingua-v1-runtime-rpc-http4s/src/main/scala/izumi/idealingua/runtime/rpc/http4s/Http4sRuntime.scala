package izumi.idealingua.runtime.rpc.http4s

import cats.effect.{ConcurrentEffect, Timer}
import izumi.functional.bio.{BIOTemporal, BIORunner}
import org.http4s.dsl._

import scala.concurrent.ExecutionContext

class Http4sRuntime[
  _BiIO[+ _, + _] : BIOTemporal : BIORunner
, _RequestContext
, _MethodContext
, _ClientId
, _ClientContext
, _ClientMethodContext
]
(
  override val clientExecutionContext: ExecutionContext
)(implicit
  C: ConcurrentEffect[_BiIO[Throwable, ?]]
, T: Timer[_BiIO[Throwable, ?]]
) extends Http4sContext {

  override type BiIO[+E, +V] = _BiIO[E, V]
  override type RequestContext = _RequestContext
  override type MethodContext = _MethodContext
  override type ClientId = _ClientId
  override type ClientContext = _ClientContext
  override type ClientMethodContext = _ClientMethodContext

  override val F: BIOTemporal[BiIO] = implicitly
  override val CIO: ConcurrentEffect[MonoIO] = implicitly
  override val CIOT: Timer[MonoIO] = implicitly
  override val BIORunner: BIORunner[BiIO] = implicitly
  override val dsl: Http4sDsl[MonoIO] = Http4sDsl[MonoIO]
}
