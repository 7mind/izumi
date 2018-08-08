package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import cats.effect.Effect
import com.github.pshirshov.izumi.idealingua.runtime.circe.{IRTClientMarshallers, IRTServerMarshallers}
import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import com.github.pshirshov.izumi.logstage.api.IzLogger
import org.http4s.dsl._

import scala.language.higherKinds


class Http4sRuntime[R[_] : IRTResult : Effect]
(
  override protected val dsl: Http4sDsl[R]
  , override protected val logger: IzLogger
  , override protected val serverMarshallers: IRTServerMarshallers
  , override protected val clientMarshallers: IRTClientMarshallers
)
  extends Http4sContext[R]
    with WithHttp4sLoggingMiddleware[R]
    with WithHttp4sClient[R]
    with WithHttp4sServer[R] {
  override protected val TM: IRTResult[R] = implicitly

  override protected def E: Effect[R] = implicitly


}
