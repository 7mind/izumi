package com.github.pshirshov.izumi.idealingua.runtime.rpc

import io.circe.{DecodingFailure, Json}
import scalaz.zio.IO

trait IRTMethodSignature extends IRTZioResult {
  type Input <: Product
  type Output <: Product

  def id: IRTMethodId
}



abstract class IRTMethodWrapper[C]() extends IRTZioResult {
  val signature: IRTMethodSignature
  val marshaller: IRTMarshaller

  def invoke(ctx: C, input: signature.Input): Just[signature.Output]
}



trait IRTMarshaller extends IRTZioResult {
  def encodeRequest: PartialFunction[IRTReqBody, Json]

  def encodeResponse: PartialFunction[IRTResBody, Json]

  def decodeRequest: PartialFunction[IRTRawCall, Just[IRTReqBody]]

  def decodeResponse: PartialFunction[IRTRawCall, Just[IRTResBody]]

  protected def decoded[V](result: Either[DecodingFailure, V]): Just[V] = {
    result match {
      case Left(f) =>
        IO.terminate(f)
      case Right(r) =>
        IO.point(r)
    }
  }
}
