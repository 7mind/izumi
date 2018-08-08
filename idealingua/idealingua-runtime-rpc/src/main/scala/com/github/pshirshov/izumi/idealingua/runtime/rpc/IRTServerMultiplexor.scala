package com.github.pshirshov.izumi.idealingua.runtime.rpc

import scala.language.higherKinds
import scala.reflect.ClassTag

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

trait IRTGeneratedUnpackingDispatcher[Ctx, R[_], In, Out <: Product]
  extends IRTUnsafeDispatcher[Ctx, R]
    with IRTDispatcher[IRTInContext[In, Ctx], Out, R]
    with IRTWithResult[R] {

  protected def toMethodId(v: In): IRTMethod

  protected def toMethodId(v: Out): IRTMethod

  protected def toZeroargBody(v: IRTMethod): Option[In]


  protected def inputTag: ClassTag[In]
  protected def outputTag: ClassTag[Out]

  def dispatchZeroargUnsafe(input: IRTInContext[IRTMethod, Ctx]): Either[DispatchingFailure, Result[IRTMuxResponse[Product]]] = {
    val maybeResult = toZeroargBody(input.value)
    maybeResult match {
      case Some(b) => Right(_ServiceResult.map(dispatch(IRTInContext(b, input.context)))(v => IRTMuxResponse(v, toMethodId(v))))
      case None => Left(DispatchingFailure.NoHandler)
    }
  }

  def dispatchUnsafe(input: IRTInContext[IRTMuxRequest[Product], Ctx]): Either[DispatchingFailure, Result[IRTMuxResponse[Product]]] = {
    implicit val inTag: ClassTag[In] = inputTag
    input.value.v match {
      case v: In =>
        Right(_ServiceResult.map(dispatch(IRTInContext(v, input.context)))(v => IRTMuxResponse(v, toMethodId(v))))

      case _ =>
        dispatchZeroargUnsafe(IRTInContext(input.value.method, input.context))
    }
  }
}
