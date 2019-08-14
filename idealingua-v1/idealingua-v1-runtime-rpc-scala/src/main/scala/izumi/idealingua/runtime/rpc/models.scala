package izumi.idealingua.runtime.rpc

import io.circe.Json


// addressing
final case class IRTServiceId(value: String) extends AnyVal {
  override def toString: String = s"{service:$value}"
}

final case class IRTMethodName(value: String) extends AnyVal {
  override def toString: String = s"{method:$value}"
}

final case class IRTMethodId(service: IRTServiceId, methodId: IRTMethodName) {
  override def toString: String = s"${service.value}.${methodId.value}"
}


// dtos

final case class IRTReqBody(value: Product) extends AnyRef

final case class IRTResBody(value: Product) extends AnyRef

final case class IRTMuxResponse(body: IRTResBody, method: IRTMethodId)

final case class IRTMuxRequest(body: IRTReqBody, method: IRTMethodId)

final case class IRTJsonBody(methodId: IRTMethodId, body: Json)
