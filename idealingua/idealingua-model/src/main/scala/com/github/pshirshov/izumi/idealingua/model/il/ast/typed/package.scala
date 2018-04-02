package com.github.pshirshov.izumi.idealingua.model.il.ast

import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.InterfaceId

package object typed {
  type Composite = List[InterfaceId]
  type Tuple = List[Field]
  type PrimitiveTuple = List[PrimitiveField]
  type TypeList = List[TypeId]
}
