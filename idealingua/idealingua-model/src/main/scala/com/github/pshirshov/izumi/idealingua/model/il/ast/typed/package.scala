package com.github.pshirshov.izumi.idealingua.model.il.ast

import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.InterfaceId

package object typed {
  type Interfaces = List[InterfaceId]
  type Types = List[TypeId]
  type Tuple = List[Field]
  type PrimitiveTuple = List[PrimitiveField]
}
