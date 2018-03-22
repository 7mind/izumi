package com.github.pshirshov.izumi.idealingua.model.runtime.transport

import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.runtime.model.{IDLGenerated, IDLGeneratedType, IDLService}

import scala.language.higherKinds

trait AbstractTransport[R[_], Service <: IDLService[R]] {
  def service: Service

  def inAcceptable(in: IDLGenerated): Boolean = service.inputClass.isAssignableFrom(in.getClass)

  def process(request: Service#InputType): R[Service#OutputType]

  def processUnsafe(request: IDLGenerated): R[Service#OutputType] = {
    request match {
      case v if inAcceptable(request) =>
        process(v.asInstanceOf[Service#InputType])

      case o =>
        throw new IDLException(s"Bad request: $o")
    }
  }
}
