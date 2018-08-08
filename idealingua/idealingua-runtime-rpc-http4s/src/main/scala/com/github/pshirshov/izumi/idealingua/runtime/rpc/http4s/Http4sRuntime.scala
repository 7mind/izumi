package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import cats._
import cats.effect.Sync
import com.github.pshirshov.izumi.idealingua.runtime.circe.IRTServerMarshallers
import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import com.github.pshirshov.izumi.logstage.api.IzLogger
import org.http4s.dsl._

import scala.language.higherKinds




class Http4sRuntime[R[_] : IRTResult : Monad : Sync]
(
  override protected val logger: IzLogger
  , override protected val dsl: Http4sDsl[R]
  , override protected val marshallers: IRTServerMarshallers
)
  extends Http4sContext[R]
    with WithHttp4sLoggingMiddleware[R]
    with WithHttp4sClient[R]
    with WithHttp4sServer[R] {
  override protected val TM: IRTResult[R] = implicitly[IRTResult[R]]

  override protected val R: Monad[R] = implicitly[Monad[R]]

  override protected def S: Sync[R] = implicitly


}
