package com.github.pshirshov.izumi.idealingua.model.runtime.transport

import com.github.pshirshov.izumi.idealingua.model.runtime.model.{IDLGenerated, IDLGeneratedType, IDLService}

trait AbstractTransport[Service <: IDLService] {
  def service: Service

  def inAcceptable(in: IDLGeneratedType): Boolean = service.inputTag.runtimeClass.isAssignableFrom(in.getClass)

  def process(request: Service#InputType): Service#Result[Service#OutputType]

  def processUnsafe[R](request: IDLGenerated): R = {
    val unsafeRequest = request.asInstanceOf[Service#InputType]
    process(unsafeRequest).asInstanceOf[R]
  }
}
