package com.github.pshirshov.izumi.idealingua.runtime.rpc

import scalaz.zio.IO

import scala.language.higherKinds
import scala.reflect.ClassTag

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

final case class IRTMuxResponse[T <: Product](v: T, method: IRTMethodId) {
  def body: IRTResBody = IRTResBody(v)
}

final case class IRTMuxRequest[T <: Product](v: T, method: IRTMethodId) {
  def body: IRTReqBody = IRTReqBody(v)
}

//sealed trait DispatchingFailure
//
//object DispatchingFailure {
//
//  case object NoHandler extends DispatchingFailure
//
//  case object Rejected extends DispatchingFailure
//
//  case class Thrown(t: Throwable) extends DispatchingFailure
//
//}
//
//
//trait IRTUnsafeDispatcher[Ctx] extends IRTZioResult {
//  def identifier: IRTServiceId
//
//  type UnsafeInput = IRTInContext[IRTMuxRequest[Product], Ctx]
//  type MaybeOutput = Either[DispatchingFailure, Just[IRTMuxResponse[Product]]]
//
//  def dispatchUnsafe(input: UnsafeInput): MaybeOutput
//}
//
//class IRTServerMultiplexor[R[_] : IRTResult, Ctx](protected val dispatchers: List[IRTUnsafeDispatcher[Ctx]])
//  extends IRTDispatcher[IRTInContext[IRTMuxRequest[Product], Ctx], Either[DispatchingFailure, IRTMuxResponse[Product]]]
//    with IRTZioResult {
//  type Input = IRTInContext[IRTMuxRequest[Product], Ctx]
//  type Output = IRTMuxResponse[Product]
//
//  def dispatch(input: Input): Just[Either[DispatchingFailure, Output]] = {
//    dispatchers.foreach {
//      d =>
//        d.dispatchUnsafe(input) match {
//          case Right(v) =>
//            return v.map(v => Right(IRTMuxResponse(v.v, v.method)))
//
//          case Left(DispatchingFailure.NoHandler) =>
//
//          case Left(t) =>
//            return IO.point(Left(t))
//        }
//    }
//
//    IO.point(Left(DispatchingFailure.NoHandler))
//  }
//}
//
//trait IRTGeneratedUnpackingDispatcher[Ctx, In, Out <: Product]
//  extends IRTUnsafeDispatcher[Ctx]
//    with IRTDispatcher[IRTInContext[In, Ctx], Out]
//    with IRTZioResult {
//
//  protected def toMethodId(v: In): IRTMethodId
//
//  protected def toMethodId(v: Out): IRTMethodId
//
//  protected def inputTag: ClassTag[In]
//
//  protected def outputTag: ClassTag[Out]
//
//  protected def toZeroargBody(v: IRTMethodId): Option[In]
//
//  def dispatchZeroargUnsafe(input: IRTInContext[IRTMethodId, Ctx]): Either[DispatchingFailure, Just[IRTMuxResponse[Product]]] = {
//    val maybeResult = toZeroargBody(input.value)
//    maybeResult match {
//      case Some(b) =>
//        val dispatched = dispatch(IRTInContext(b, input.context))
//        Right(dispatched.map(v => IRTMuxResponse(v, toMethodId(v))))
//
//      case None =>
//        Left(DispatchingFailure.NoHandler)
//    }
//  }
//
//  def dispatchUnsafe(input: IRTInContext[IRTMuxRequest[Product], Ctx]): Either[DispatchingFailure, Just[IRTMuxResponse[Product]]] = {
//    implicit val inTag: ClassTag[In] = inputTag
//    input.value.v match {
//      case v: In =>
//        val dispatched = dispatch(IRTInContext(v, input.context))
//        Right(dispatched.map(v => IRTMuxResponse(v, toMethodId(v))))
//
//      case _ =>
//        dispatchZeroargUnsafe(IRTInContext(input.value.method, input.context))
//    }
//  }
//}
