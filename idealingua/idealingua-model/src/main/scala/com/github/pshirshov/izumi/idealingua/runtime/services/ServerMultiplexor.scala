package com.github.pshirshov.izumi.idealingua.runtime.services

import scala.language.higherKinds

trait UnsafeDispatcher[Ctx, R[_]] extends WithResultType[R] {
  def identifier: ServiceId

  def dispatchUnsafe(input: InContext[MuxRequest[_], Ctx]): Option[Result[MuxResponse[_]]]
}

case class Method(service: ServiceId, methodId: MethodId)

//case class ReqBody(value: AnyRef) extends AnyRef
//
//case class ResBody(value: AnyRef) extends AnyRef
//
//case class MuxResponse[T <: AnyRef](v: T, method: Method) {
//  def body: ResBody = ResBody(v)
//}
//
//case class MuxRequest[T <: AnyRef](v: T, method: Method) {
//  def body: ReqBody = ReqBody(v)
//}

case class ReqBody(value: Any) extends AnyRef

case class ResBody(value: Any) extends AnyRef

case class MuxResponse[T](v: T, method: Method) {
  def body: ResBody = ResBody(v)
}

case class MuxRequest[T](v: T, method: Method) {
  def body: ReqBody = ReqBody(v)
}

case class ServiceId(value: String) extends AnyVal

case class MethodId(value: String) extends AnyVal


class ServerMultiplexor[R[_], Ctx](dispatchers: List[UnsafeDispatcher[Ctx, R]]) extends Dispatcher[InContext[MuxRequest[_], Ctx], MuxResponse[_], R] {
  override def dispatch(input: InContext[MuxRequest[_], Ctx]): Result[MuxResponse[_]] = {
    dispatchers.foreach {
      d =>
        d.dispatchUnsafe(input) match {
          case Some(v) =>
            return v
          case None =>
        }
    }
    throw new MultiplexingException(s"Cannot handle $input, services: $dispatchers", input)
  }
}
