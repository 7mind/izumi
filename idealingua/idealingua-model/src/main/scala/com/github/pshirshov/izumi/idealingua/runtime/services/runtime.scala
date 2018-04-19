package com.github.pshirshov.izumi.idealingua.runtime.services

import scala.language.higherKinds

case class InContext[V, Ctx](value: V, context: Ctx)


trait IRTTransport[RequestWire, ResponseWire] {
  def send(v: RequestWire): ResponseWire
}

trait IRTTransportMarshallers[RequestWire, Request, Response, ResponseWire] {
  def decodeRequest(requestWire: RequestWire): Request

  def encodeRequest(request: Request): RequestWire

  def decodeResponse(responseWire: ResponseWire): Response

  def encodeResponse(response: Response): ResponseWire
}



class IRTServerReceiver[RequestWire, Request, Response, ResponseWire, R[_] : IRTServiceResult]
(
  dispatcher: IRTDispatcher[Request, Response, R]
  , codec: IRTTransportMarshallers[RequestWire, Request, Response, ResponseWire]
) extends IRTReceiver[RequestWire, ResponseWire, R] with IRTWithSvcResult[R] {
  override protected def _ServiceResult: IRTServiceResult[R] = implicitly

  def receive(request: RequestWire): R[ResponseWire] = {
    import IRTServiceResult._
    _Result(codec.decodeRequest(request))
      .flatMap(dispatcher.dispatch)
      .map(codec.encodeResponse)
  }
}


class IRTClientDispatcher[RequestWire, Request, Response, ResponseWire, R[_] : IRTServiceResult]
(
  transport: IRTTransport[RequestWire, R[ResponseWire]]
  , codec: IRTTransportMarshallers[RequestWire, Request, Response, ResponseWire]
) extends IRTDispatcher[Request, Response, R] with IRTWithSvcResult[R] {
  override protected def _ServiceResult: IRTServiceResult[R] = implicitly

  def dispatch(input: Request): Result[Response] = {
    import IRTServiceResult._
    _Result(codec.encodeRequest(input))
      .flatMap(transport.send)
      .map(codec.decodeResponse)
  }
}




