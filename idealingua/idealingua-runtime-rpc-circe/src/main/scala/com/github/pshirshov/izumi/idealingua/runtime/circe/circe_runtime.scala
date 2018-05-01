package com.github.pshirshov.izumi.idealingua.runtime.circe

import com.github.pshirshov.izumi.idealingua.runtime.rpc.{IRTIdentifiableServiceDefinition, IRTMethod, IRTReqBody, IRTResBody}
import io.circe._


trait IRTCirceWrappedServiceDefinition {
  this: IRTIdentifiableServiceDefinition =>
  def codecProvider: IRTMuxingCodecProvider
}

trait IRTClientMarshallers {
  def encodeRequest(request: IRTReqBody): String

  def decodeResponse(responseWire: String, m: IRTMethod): Either[Error, IRTResBody]
}

trait IRTServerMarshallers {
  def decodeRequest(requestWire: String, m: IRTMethod): Either[Error, IRTReqBody]

  def encodeResponse(response: IRTResBody): String
}

class IRTOpinionatedMarshalers(provider: List[IRTMuxingCodecProvider]) extends IRTClientMarshallers with IRTServerMarshallers {

  import _root_.io.circe.parser._
  import _root_.io.circe.syntax._

  def decodeRequest(requestWire: String, m: IRTMethod): Either[Error, IRTReqBody] = {
    implicit val x: IRTMethod = m
    val parsed = parse(requestWire).flatMap(_.as[IRTReqBody])
    parsed
  }

  def decodeResponse(responseWire: String, m: IRTMethod): Either[Error, IRTResBody] = {
    implicit val x: IRTMethod = m
    val parsed = parse(responseWire).flatMap(_.as[IRTResBody])
    parsed
  }

  def encodeRequest(request: IRTReqBody): String = {
    request.asJson.noSpaces
  }

  def encodeResponse(response: IRTResBody): String = {
    response.asJson.noSpaces
  }

  private val requestEncoders = provider.flatMap(_.requestEncoders)
  private val responseEncoders = provider.flatMap(_.responseEncoders)
  private val requestDecoders = provider.flatMap(_.requestDecoders)
  private val responseDecoders = provider.flatMap(_.responseDecoders)

  private implicit val encodePolymorphicRequest: Encoder[IRTReqBody] = Encoder.instance { c =>
    requestEncoders.foldLeft(PartialFunction.empty[IRTReqBody, Json])(_ orElse _)(c)
  }

  private implicit val encodePolymorphicResponse: Encoder[IRTResBody] = Encoder.instance { c =>
    responseEncoders.foldLeft(PartialFunction.empty[IRTResBody, Json])(_ orElse _)(c)
  }

  private implicit def decodePolymorphicRequest(implicit method: IRTMethod): Decoder[IRTReqBody] = Decoder.instance(c => {
    requestDecoders.foldLeft(PartialFunction.empty[IRTCursorForMethod, Decoder.Result[IRTReqBody]])(_ orElse _)(IRTCursorForMethod(method, c))
  })

  private  implicit def decodePolymorphicResponse(implicit method: IRTMethod): Decoder[IRTResBody] = Decoder.instance(c => {
    responseDecoders.foldLeft(PartialFunction.empty[IRTCursorForMethod, Decoder.Result[IRTResBody]])(_ orElse _)(IRTCursorForMethod(method, c))
  })
}


object IRTOpinionatedMarshalers {
  def apply(definitions: List[IRTCirceWrappedServiceDefinition]) = new IRTOpinionatedMarshalers(definitions.map(_.codecProvider))
}


final case class IRTCursorForMethod(methodId: IRTMethod, cursor: HCursor)

trait IRTMuxingCodecProvider {
  def requestEncoders: List[PartialFunction[IRTReqBody, Json]]

  def responseEncoders: List[PartialFunction[IRTResBody, Json]]

  def requestDecoders: List[PartialFunction[IRTCursorForMethod, Decoder.Result[IRTReqBody]]]

  def responseDecoders: List[PartialFunction[IRTCursorForMethod, Decoder.Result[IRTResBody]]]

}
