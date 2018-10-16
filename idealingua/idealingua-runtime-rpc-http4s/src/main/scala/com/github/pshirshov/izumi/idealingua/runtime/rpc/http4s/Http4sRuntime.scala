package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import _root_.io.circe.Printer
import cats.effect.{ConcurrentEffect, Timer}
import com.github.pshirshov.izumi.functional.bio.{BIOAsync, BIORunner, CIORunner}
import com.github.pshirshov.izumi.logstage.api.IzLogger
import org.http4s.dsl._

import scala.concurrent.ExecutionContext
import scala.language.higherKinds


class Http4sRuntime[_BiIO[+ _, + _] : BIOAsync : BIORunner, _CatsIO[+ _] : ConcurrentEffect : CIORunner : Timer]
(
  override protected val logger: IzLogger
  , override protected val clientExecutionContext: ExecutionContext
)
  extends Http4sContext
    with WithHttp4sLoggingMiddleware
    with WithHttp4sClient
    with WithHttp4sWsClient
    with WithHttp4sHttpRequestContext
    with WithWebsocketClientContext
    with WithHttp4sServer {

  override type BiIO[+E, +V] = _BiIO[E, V]

  override type CatsIO[+T] = _CatsIO[T]

  override protected val BIO: BIOAsync[BiIO] = implicitly

  override protected val CIO: ConcurrentEffect[CatsIO] = implicitly

  override protected val CIOT: Timer[CatsIO] = implicitly

  override protected val CIORunner: CIORunner[_CatsIO] = implicitly

  override protected val BIORunner: BIORunner[BiIO] = implicitly

  override protected val dsl: Http4sDsl[CatsIO] = Http4sDsl.apply[CatsIO]

  override protected def printer: Printer = Printer.noSpaces.copy(dropNullValues = true)
}
