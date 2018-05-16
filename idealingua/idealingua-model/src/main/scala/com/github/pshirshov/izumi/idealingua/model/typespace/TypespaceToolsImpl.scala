package com.github.pshirshov.izumi.idealingua.model.typespace

import com.github.pshirshov.izumi.idealingua.model.common.TypeId

class TypespaceToolsImpl() extends TypespaceTools {
  override def idToParaName(id: TypeId): String = id.name.toLowerCase
}
