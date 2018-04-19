package com.github.pshirshov.izumi.idealingua.runtime.services

import scala.language.higherKinds

trait IRTIdentifiableServiceDefinition {
  def serviceId: IRTServiceId
}

trait IRTDispatcher[In, Out, R[_]] extends IRTWithSvcResultType[R] {
  def dispatch(input: In): Result[Out]
}

trait IRTReceiver[In, Out, R[_]] extends IRTWithSvcResultType[R] {
  def receive(input: In): Result[Out]
}

trait IRTWrappedServiceDefinition {
  this: IRTIdentifiableServiceDefinition =>

  type Input
  type Output
  type ServiceServer[_[_], _]
  type ServiceClient[_[_]]

  def client[R[_] : IRTServiceResult](dispatcher: IRTDispatcher[Input, Output, R]): ServiceClient[R]


  def server[R[_] : IRTServiceResult, C](service: ServiceServer[R, C]): IRTDispatcher[IRTInContext[Input, C], Output, R]

}


trait IRTWrappedUnsafeServiceDefinition {
  this: IRTWrappedServiceDefinition =>
  def clientUnsafe[R[_] : IRTServiceResult](dispatcher: IRTDispatcher[IRTMuxRequest[_], IRTMuxResponse[_], R]): ServiceClient[R]

  def serverUnsafe[R[_] : IRTServiceResult, C](service: ServiceServer[R, C]): IRTUnsafeDispatcher[C, R]

}
