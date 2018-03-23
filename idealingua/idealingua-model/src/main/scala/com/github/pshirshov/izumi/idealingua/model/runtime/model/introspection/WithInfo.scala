package com.github.pshirshov.izumi.idealingua.model.runtime.model.introspection

import com.github.pshirshov.izumi.idealingua.model.runtime.model.IDLGeneratedType

trait WithInfo extends Any {
  this: IDLGeneratedType =>
  def _info: IDLTypeInfo
}
