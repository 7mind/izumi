package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import cats._
import cats.effect.Sync
import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import com.github.pshirshov.izumi.logstage.api.IzLogger
import org.http4s._
import org.http4s.dsl._

import scala.language.higherKinds

trait Http4sContext[R[_]] {
  type MaterializedStream = String
  type StreamDecoder = EntityDecoder[R, MaterializedStream]

  protected def dsl: Http4sDsl[R]

  protected def TM: IRTResult[R]

  protected def logger: IzLogger

  protected implicit def R: Monad[R]

  protected implicit def S: Sync[R]
}
