//package com.github.pshirshov.izumi.idealingua.runtime.rpc
//
//import scala.language.higherKinds
//
//trait IRTIdentifiableServiceDefinition {
//  def serviceId: IRTServiceId
//}
//
//trait IRTDispatcher[In, Out] extends IRTResult {
//  def dispatch(input: In): Just[Out]
//}
//
//trait IRTReceiver[In, Out, R[_]] extends IRTResult {
//  def receive(input: In): Just[Out]
//}
//
//trait IRTWrappedServiceDefinition {
//  this: IRTIdentifiableServiceDefinition =>
//
//  type Input
//  type Output
//  type ServiceServer[_]
//  type ServiceClient
//
//  def client(dispatcher: IRTDispatcher[Input, Output]): ServiceClient
//
//
//  def server[C](service: ServiceServer[C]): IRTDispatcher[IRTInContext[Input, C], Output]
//
//}
//
//
//trait IRTWrappedUnsafeServiceDefinition {
//  this: IRTWrappedServiceDefinition =>
//  def clientUnsafe(dispatcher: IRTDispatcher[IRTMuxRequest[Product], IRTMuxResponse[Product]]): ServiceClient
//
//  def serverUnsafe[C](service: ServiceServer[C]): IRTUnsafeDispatcher[C]
//
//}
