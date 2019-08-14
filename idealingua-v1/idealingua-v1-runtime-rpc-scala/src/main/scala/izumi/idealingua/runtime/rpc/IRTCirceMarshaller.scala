package izumi.idealingua.runtime.rpc

import izumi.functional.bio.BIO
import io.circe.{DecodingFailure, Json}

abstract class IRTCirceMarshaller {
  def encodeRequest: PartialFunction[IRTReqBody, Json]

  def encodeResponse: PartialFunction[IRTResBody, Json]

  def decodeRequest[Or[+_, +_] : BIO]: PartialFunction[IRTJsonBody, Or[DecodingFailure, IRTReqBody]]

  def decodeResponse[Or[+_, +_] : BIO]: PartialFunction[IRTJsonBody, Or[DecodingFailure, IRTResBody]]

  protected def decoded[Or[+_, +_] : BIO, V](result: Either[DecodingFailure, V]): Or[DecodingFailure, V] = {
    BIO[Or].fromEither(result)
  }
}
