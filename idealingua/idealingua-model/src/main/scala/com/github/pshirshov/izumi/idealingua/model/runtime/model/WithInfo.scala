package com.github.pshirshov.izumi.idealingua.model.runtime.model

trait WithInfo extends Any {
  this: IDLGeneratedType =>
  def _info: IDLTypeInfo
}
