package com.github.pshirshov.izumi.idealingua.model.runtime.transport

import com.github.pshirshov.izumi.idealingua.model.runtime.model.{IDLGenerated, IDLGeneratedType, IDLOutput, IDLService}

trait AbstractTransport[Service <: IDLService] {
  def service: Service

  def inAcceptable(in: IDLGeneratedType): Boolean = service.inputTag.runtimeClass.isAssignableFrom(in.getClass)

  def outAcceptable(out: IDLGeneratedType): Boolean = service.outputTag.runtimeClass.isAssignableFrom(out.getClass)

  def process(request: Service#InputType): Service#OutputType

  def processUnsafe(request: IDLGenerated): IDLOutput = {
    process(request.asInstanceOf[Service#InputType])
  }
}
