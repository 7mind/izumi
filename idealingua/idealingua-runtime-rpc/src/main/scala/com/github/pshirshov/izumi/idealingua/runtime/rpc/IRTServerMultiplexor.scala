package com.github.pshirshov.izumi.idealingua.runtime.rpc

import scala.language.higherKinds

// addressing
case class IRTServiceId(value: String) extends AnyVal

case class IRTMethodId(value: String) extends AnyVal

case class IRTMethod(service: IRTServiceId, methodId: IRTMethodId)


// dtos

case class IRTReqBody(value: Product) extends AnyRef

case class IRTResBody(value: Product) extends AnyRef

case class IRTMuxResponse[T <: Product](v: T, method: IRTMethod) {
  def body: IRTResBody = IRTResBody(v)
}

case class IRTMuxRequest[T <: Product](v: T, method: IRTMethod) {
  def body: IRTReqBody = IRTReqBody(v)
}


trait IRTUnsafeDispatcher[Ctx, R[_]] extends IRTWithResultType[R] {
  def identifier: IRTServiceId

  def dispatchUnsafe(input: IRTInContext[IRTMuxRequest[Product], Ctx]): Option[Result[IRTMuxResponse[Product]]]
}

class IRTServerMultiplexor[R[_] : IRTServiceResult, Ctx](dispatchers: List[IRTUnsafeDispatcher[Ctx, R]])
  extends IRTDispatcher[IRTInContext[IRTMuxRequest[Product], Ctx], IRTMuxResponse[Product], R]
    with IRTWithResult[R] {
  override protected def _ServiceResult: IRTServiceResult[R] = implicitly

  type Input = IRTInContext[IRTMuxRequest[Product], Ctx]
  type Output = IRTMuxResponse[Product]

  override def dispatch(input: Input): Result[Output] = {
    dispatchers.foreach {
      d =>
        d.dispatchUnsafe(input) match {
          case Some(v) =>
            return _ServiceResult.map(v)(v => IRTMuxResponse(v.v, v.method))
          case None =>
        }
    }
    throw new IRTMultiplexingException(s"Cannot handle $input, services: $dispatchers", input, None)
  }
}
