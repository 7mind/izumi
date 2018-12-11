package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.IndefiniteMixin
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.InterfaceId

package object defns {
  type RawInterfaces = List[InterfaceId]
  type RawStructures = List[IndefiniteMixin]
  type RawTuple = List[RawField]
}
