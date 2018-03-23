package com.github.pshirshov.izumi.idealingua.runtime.transport

import com.github.pshirshov.izumi.idealingua.runtime.model.IDLService

import scala.language.higherKinds


trait AbstractServerDispatcher[R[+_], Service <: IDLService[R]] {
  def dispatch(request: Service#InputType): R[Service#OutputType]
}

trait AbstractClientDispatcher[R[+_], Service <: IDLService[R]] {
  def dispatch[I <: Service#InputType, O <: Service#OutputType](request: I, result: Class[O]): R[O]
}
