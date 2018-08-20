package com.github.pshirshov.izumi.idealingua.runtime.rpc

import io.circe.Json

import scala.language.higherKinds

abstract class IRTCirceMarshaller {
  def encodeRequest: PartialFunction[IRTReqBody, Json]

  def encodeResponse: PartialFunction[IRTResBody, Json]

  def decodeRequest[Or[+_, +_] : IRTResult]: PartialFunction[IRTJsonBody, Or[Nothing, IRTReqBody]]

  def decodeResponse[Or[+_, +_] : IRTResult]: PartialFunction[IRTJsonBody, Or[Nothing, IRTResBody]]
}
