package com.github.pshirshov.izumi.idealingua.runtime.circe

import com.github.pshirshov.izumi.idealingua.runtime.services._
import io.circe._


trait IRTCirceWrappedServiceDefinition {
  this: IRTIdentifiableServiceDefinition =>
  def codecProvider: IRTMuxingCodecProvider
}

trait IRTMuxedCodec {
  implicit val encodePolymorphicRequest: Encoder[IRTReqBody]
  implicit val encodePolymorphicResponse: Encoder[IRTResBody]

  implicit def decodePolymorphicRequest(implicit method: IRTMethod): Decoder[IRTReqBody]
  implicit def decodePolymorphicResponse(implicit method: IRTMethod): Decoder[IRTResBody]
}

case class IRTCursorForMethod(methodId: IRTMethod, cursor: HCursor)

trait IRTMuxingCodecProvider {
  def requestEncoders: List[PartialFunction[IRTReqBody, Json]]

  def responseEncoders: List[PartialFunction[IRTResBody, Json]]

  def requestDecoders: List[PartialFunction[IRTCursorForMethod, Decoder.Result[IRTReqBody]]]

  def responseDecoders: List[PartialFunction[IRTCursorForMethod, Decoder.Result[IRTResBody]]]

}

class IRTOpinionatedMuxedCodec
(
  provider: List[IRTMuxingCodecProvider]
) extends IRTMuxedCodec {

  private val requestEncoders = provider.flatMap(_.requestEncoders)
  private val responseEncoders = provider.flatMap(_.responseEncoders)
  private val requestDecoders = provider.flatMap(_.requestDecoders)
  private val responseDecoders = provider.flatMap(_.responseDecoders)

  override implicit val encodePolymorphicRequest: Encoder[IRTReqBody] = Encoder.instance { c =>
    requestEncoders.foldLeft(PartialFunction.empty[IRTReqBody, Json])(_ orElse _)(c)

  }

  override implicit val encodePolymorphicResponse: Encoder[IRTResBody] = Encoder.instance { c =>
    responseEncoders.foldLeft(PartialFunction.empty[IRTResBody, Json])(_ orElse _)(c)

  }

  override implicit def decodePolymorphicRequest(implicit method: IRTMethod): Decoder[IRTReqBody] = Decoder.instance(c => {
    requestDecoders.foldLeft(PartialFunction.empty[IRTCursorForMethod, Decoder.Result[IRTReqBody]])(_ orElse _)(IRTCursorForMethod(method, c))
  })

  override implicit def decodePolymorphicResponse(implicit method: IRTMethod): Decoder[IRTResBody] = Decoder.instance(c => {
    responseDecoders.foldLeft(PartialFunction.empty[IRTCursorForMethod, Decoder.Result[IRTResBody]])(_ orElse _)(IRTCursorForMethod(method, c))
  })

}

object IRTOpinionatedMuxedCodec {
  def apply(definitions: List[IRTCirceWrappedServiceDefinition]) = new IRTOpinionatedMuxedCodec(definitions.map(_.codecProvider))
}
