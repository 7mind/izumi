package com.github.pshirshov.izumi.idealingua.runtime.circe

import com.github.pshirshov.izumi.idealingua.runtime.services._
import io.circe._


trait CirceWrappedServiceDefinition {
  this: IdentifiableServiceDefinition =>
  def codecProvider: MuxingCodecProvider
}

trait MuxedCodec {
  implicit val encodePolymorphicRequest: Encoder[ReqBody]
  implicit val encodePolymorphicResponse: Encoder[ResBody]

  implicit def decodePolymorphicRequest(implicit method: Method): Decoder[ReqBody]
  implicit def decodePolymorphicResponse(implicit method: Method): Decoder[ResBody]
}

case class CursorForMethod(methodId: Method, cursor: HCursor)

trait MuxingCodecProvider {
  def requestEncoders: List[PartialFunction[ReqBody, Json]]

  def responseEncoders: List[PartialFunction[ResBody, Json]]

  def requestDecoders: List[PartialFunction[CursorForMethod, Decoder.Result[ReqBody]]]

  def responseDecoders: List[PartialFunction[CursorForMethod, Decoder.Result[ResBody]]]

}

class OpinionatedMuxedCodec
(
  provider: List[MuxingCodecProvider]
) extends MuxedCodec {

  private val requestEncoders = provider.flatMap(_.requestEncoders)
  private val responseEncoders = provider.flatMap(_.responseEncoders)
  private val requestDecoders = provider.flatMap(_.requestDecoders)
  private val responseDecoders = provider.flatMap(_.responseDecoders)

  override implicit val encodePolymorphicRequest: Encoder[ReqBody] = Encoder.instance { c =>
    requestEncoders.foldLeft(PartialFunction.empty[ReqBody, Json])(_ orElse _)(c)

  }

  override implicit val encodePolymorphicResponse: Encoder[ResBody] = Encoder.instance { c =>
    responseEncoders.foldLeft(PartialFunction.empty[ResBody, Json])(_ orElse _)(c)

  }

  override implicit def decodePolymorphicRequest(implicit method: Method): Decoder[ReqBody] = Decoder.instance(c => {
    requestDecoders.foldLeft(PartialFunction.empty[CursorForMethod, Decoder.Result[ReqBody]])(_ orElse _)(CursorForMethod(method, c))
  })

  override implicit def decodePolymorphicResponse(implicit method: Method): Decoder[ResBody] = Decoder.instance(c => {
    responseDecoders.foldLeft(PartialFunction.empty[CursorForMethod, Decoder.Result[ResBody]])(_ orElse _)(CursorForMethod(method, c))
  })

}

object OpinionatedMuxedCodec {
  def apply(definitions: List[CirceWrappedServiceDefinition]) = new OpinionatedMuxedCodec(definitions.map(_.codecProvider))
}
