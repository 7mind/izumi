package com.github.pshirshov.izumi.idealingua.runtime.rpc

import scala.language.higherKinds

// addressing
final case class IRTServiceId(value: String) extends AnyVal {
  override def toString: String = s"{service:$value}"
}

final case class IRTMethodId(value: String) extends AnyVal {
  override def toString: String = s"{method:$value}"
}

final case class IRTMethod(service: IRTServiceId, methodId: IRTMethodId) {
  override def toString: String = s"${service.value}.${methodId.value}"
}


// dtos

final case class IRTReqBody(value: Product) extends AnyRef

final case class IRTResBody(value: Product) extends AnyRef

final case class IRTMuxResponse[T <: Product](v: T, method: IRTMethod) {
  def body: IRTResBody = IRTResBody(v)
}

final case class IRTMuxRequest[T <: Product](v: T, method: IRTMethod) {
  def body: IRTReqBody = IRTReqBody(v)
}

sealed trait DispatchingFailure

object DispatchingFailure {

  case object NoHandler extends DispatchingFailure

  case object Rejected extends DispatchingFailure

  case class Thrown(t: Throwable) extends DispatchingFailure

}


trait IRTUnsafeDispatcher[Ctx, R[_]] extends IRTWithResultType[R] {
  def identifier: IRTServiceId

  type UnsafeInput = IRTInContext[IRTMuxRequest[Product], Ctx]
  type MaybeOutput = Either[DispatchingFailure, Result[IRTMuxResponse[Product]]]

  def dispatchUnsafe(input: UnsafeInput): MaybeOutput
}

class IRTServerMultiplexor[R[_] : IRTResult, Ctx](dispatchers: List[IRTUnsafeDispatcher[Ctx, R]])
  extends IRTDispatcher[IRTInContext[IRTMuxRequest[Product], Ctx], Either[DispatchingFailure, IRTMuxResponse[Product]], R]
    with IRTWithResult[R] {
  override protected def _ServiceResult: IRTResult[R] = implicitly

  type Input = IRTInContext[IRTMuxRequest[Product], Ctx]
  type Output = IRTMuxResponse[Product]

  override def dispatch(input: Input): Result[Either[DispatchingFailure, Output]] = {
    dispatchers.foreach {
      d =>
        d.dispatchUnsafe(input) match {
          case Right(v) =>
            return _ServiceResult.map(v)(v => Right(IRTMuxResponse(v.v, v.method)))
          case Left(DispatchingFailure.NoHandler) =>
          case Left(t) =>
            return _ServiceResult.wrap(Left(t))
        }
    }

    _ServiceResult.wrap(Left(DispatchingFailure.NoHandler))
  }
}
