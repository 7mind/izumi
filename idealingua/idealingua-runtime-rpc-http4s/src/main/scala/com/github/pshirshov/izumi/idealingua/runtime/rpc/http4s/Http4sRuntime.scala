package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import cats.effect.{ConcurrentEffect, Timer}
import com.github.pshirshov.izumi.idealingua.runtime.rpc.IRTResultTransZio
import com.github.pshirshov.izumi.logstage.api.IzLogger
import org.http4s.dsl._

import scala.language.higherKinds


class Http4sRuntime[ZIO[+ _, + _] : IRTResultTransZio, CaIO[+ _] : ConcurrentEffect : CIORunner : Timer]
(
  override protected val logger: IzLogger
)
  extends Http4sContext
    with WithHttp4sLoggingMiddleware
    with WithHttp4sClient
    with WithHttp4sWsClient
    with WithHttp4sHttpRequestContext
    with WithWebsocketClientContext
    with WithHttp4sServer {


  override type BIO[+E, +V] = ZIO[E, V]

  override protected val BIO: IRTResultTransZio[BIO] = implicitly

  override type CIO[+T] = CaIO[T]

  override protected val CIO: ConcurrentEffect[CIO] = implicitly[ConcurrentEffect[CIO]]
  override protected val CIOT: Timer[CIO] = implicitly[Timer[CIO]]

  override protected val dsl: Http4sDsl[CIO] = Http4sDsl.apply[CIO]

  override protected def CIORunner: CIORunner[CaIO] = implicitly

}
