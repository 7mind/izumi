package com.github.pshirshov.izumi.idealingua.runtime.rpc

import io.circe.{DecodingFailure, Json}

import scala.language.higherKinds

abstract class IRTCirceMarshaller[R[_, _] : IRTResult] {
  val R: IRTResult[R] = implicitly[IRTResult[R]]

  def encodeRequest: PartialFunction[IRTReqBody, Json]

  def encodeResponse: PartialFunction[IRTResBody, Json]

  def decodeRequest: PartialFunction[IRTJsonBody, R.Just[IRTReqBody]]

  def decodeResponse: PartialFunction[IRTJsonBody, R.Just[IRTResBody]]

  protected def decoded[V](result: Either[DecodingFailure, V]): R.Just[V] = {
    R.maybe(result)
  }
}
