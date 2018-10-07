package com.github.pshirshov.izumi.idealingua.runtime.rpc

import com.github.pshirshov.izumi.functional.bio.BIO
import io.circe.{DecodingFailure, Json}

import scala.language.higherKinds

abstract class IRTCirceMarshaller {
  def encodeRequest: PartialFunction[IRTReqBody, Json]

  def encodeResponse: PartialFunction[IRTResBody, Json]

  def decodeRequest[Or[+_, +_] : BIO]: PartialFunction[IRTJsonBody, Or[Nothing, IRTReqBody]]

  def decodeResponse[Or[+_, +_] : BIO]: PartialFunction[IRTJsonBody, Or[Nothing, IRTResBody]]

  protected def decoded[Or[+_, +_] : BIO, V](result: Either[DecodingFailure, V]): Or[Nothing, V] = implicitly[BIO[Or]].maybe(result)
}
