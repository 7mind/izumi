package com.github.pshirshov.izumi.idealingua.runtime.rpc

import io.circe.{DecodingFailure, Json}
import scalaz.zio.IO

trait IRTMethodWrapper[C] extends IRTZioResult {
  type Input <: Product
  type Output <: Product

  def id: IRTMethodId

  def invoke(ctx: C, input: Input): Just[Output]
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
