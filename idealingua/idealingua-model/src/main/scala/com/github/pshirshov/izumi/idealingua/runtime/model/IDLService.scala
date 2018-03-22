package com.github.pshirshov.izumi.idealingua.runtime.model

import scala.language.higherKinds

trait IDLService[R[_]] extends IDLGeneratedType {
  type Result[T] = R[T]

  type InputType <: IDLInput
  type OutputType <: IDLOutput

  def inputClass: Class[InputType]

  def outputClass: Class[OutputType]
}
