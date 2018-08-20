package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import cats.effect.ConcurrentEffect
import com.github.pshirshov.izumi.idealingua.runtime.rpc.IRTResultTransZio
import com.github.pshirshov.izumi.logstage.api.IzLogger
import org.http4s.dsl._

import scala.language.higherKinds


class Http4sRuntime[ZIO[+ _, + _] : IRTResultTransZio, CaIO[+ _] : ConcurrentEffect : CIORunner]
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

  override protected val CIO: ConcurrentEffect[CIO] = implicitly

  override protected val dsl: Http4sDsl[CIO] = Http4sDsl.apply[CIO]

  override protected def unsafeRunSync[A](cio: CIO[A]): A = implicitly[CIORunner[CIO]].unsafeRunSync(cio)
}
