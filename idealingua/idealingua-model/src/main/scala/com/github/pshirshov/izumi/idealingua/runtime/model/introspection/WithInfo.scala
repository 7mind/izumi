package com.github.pshirshov.izumi.idealingua.runtime.model.introspection

import com.github.pshirshov.izumi.idealingua.runtime.model.IDLGeneratedType

trait WithInfo extends Any {
  this: IDLGeneratedType =>
  def _info: IDLTypeInfo
}
