package com.github.pshirshov.izumi.idealingua.runtime.transport

import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.runtime.model.{IDLGenerated, IDLService}

import scala.language.higherKinds

trait UnsafeServerDispatcher[R[+_], Service <: IDLService[R]] {
  this: AbstractServerDispatcher[R, Service] =>

  def service: Service


  def inAcceptable(in: IDLGenerated): Boolean = service.inputClass.isAssignableFrom(in.getClass)

  def dispatchUnsafe(request: IDLGenerated): R[Service#OutputType] = {
    request match {
      case v if inAcceptable(request) =>
        dispatch(v.asInstanceOf[Service#InputType])

      case o =>
        throw new IDLException(s"Bad request: $o")
    }
  }
}
