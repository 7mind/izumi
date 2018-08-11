package com.github.pshirshov.izumi.idealingua.runtime.rpc

import io.circe.{DecodingFailure, Json}

import scala.language.higherKinds

trait IRTMarshaller[R[_, _]] extends IRTResultTransZio[R] {
  def encodeRequest: PartialFunction[IRTReqBody, Json]

  def encodeResponse: PartialFunction[IRTResBody, Json]

  def decodeRequest: PartialFunction[IRTJsonBody, Just[IRTReqBody]]

  def decodeResponse: PartialFunction[IRTJsonBody, Just[IRTResBody]]

  protected def decoded[V](result: Either[DecodingFailure, V]): Just[V] = {
    maybe(result)
  }
}
