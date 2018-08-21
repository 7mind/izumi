package com.github.pshirshov.izumi.idealingua.runtime.rpc

import com.github.pshirshov.izumi.idealingua.runtime.bio.MicroBIO
import io.circe.{DecodingFailure, Json}

import scala.language.higherKinds

abstract class IRTCirceMarshaller {
  def encodeRequest: PartialFunction[IRTReqBody, Json]

  def encodeResponse: PartialFunction[IRTResBody, Json]

  def decodeRequest[Or[+_, +_] : MicroBIO]: PartialFunction[IRTJsonBody, Or[Nothing, IRTReqBody]]

  def decodeResponse[Or[+_, +_] : MicroBIO]: PartialFunction[IRTJsonBody, Or[Nothing, IRTResBody]]

  protected def decoded[Or[+_, +_] : MicroBIO, V](result: Either[DecodingFailure, V]): Or[Nothing, V] = implicitly[MicroBIO[Or]].maybe(result)
}
