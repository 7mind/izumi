package com.github.pshirshov.izumi.idealingua.runtime.rpc

import io.circe.{DecodingFailure, Json}

import scala.language.higherKinds

abstract class IRTCirceMarshaller[Or[_, _]] {
  type Just[T] = Or[Nothing, T]

  def encodeRequest: PartialFunction[IRTReqBody, Json]

  def encodeResponse: PartialFunction[IRTResBody, Json]

  def decodeRequest: PartialFunction[IRTJsonBody, Just[IRTReqBody]]

  def decodeResponse: PartialFunction[IRTJsonBody, Just[IRTResBody]]

  protected def decoded[V](result: Either[DecodingFailure, V]): Just[V]
}
