package com.github.pshirshov.izumi.idealingua.runtime.model.introspection

import com.github.pshirshov.izumi.idealingua.runtime.model.IDLGeneratedType

trait IDLWithInfo extends Any {
  this: IDLGeneratedType =>
  def _info: IDLTypeInfo
}
