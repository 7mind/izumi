package com.github.pshirshov.izumi.idealingua.runtime.rpc

import io.circe.{DecodingFailure, Json}
import scalaz.zio.IO

trait IRTMarshaller extends IRTZioResult {
  def encodeRequest: PartialFunction[IRTReqBody, Json]

  def encodeResponse: PartialFunction[IRTResBody, Json]

  def decodeRequest: PartialFunction[IRTJsonBody, Just[IRTReqBody]]

  def decodeResponse: PartialFunction[IRTJsonBody, Just[IRTResBody]]

  protected def decoded[V](result: Either[DecodingFailure, V]): Just[V] = {
    result match {
      case Left(f) =>
        IO.terminate(f)
      case Right(r) =>
        IO.point(r)
    }
  }
}
