package com.github.pshirshov.izumi.idealingua.model.runtime.transport

import com.github.pshirshov.izumi.idealingua.model.runtime.model.{IDLGenerated, IDLGeneratedType, IDLService}

import scala.language.higherKinds

trait AbstractTransport[R[_], Service <: IDLService[R]] {
  def service: Service

  def inAcceptable(in: IDLGeneratedType): Boolean = service.inputClass.isAssignableFrom(in.getClass)

  def process(request: Service#InputType): Service#Result[Service#OutputType]

  def processUnsafe(request: IDLGenerated): Service#Result[Service#OutputType] = {
    val unsafeRequest = request.asInstanceOf[Service#InputType]
    process(unsafeRequest)
  }
}
