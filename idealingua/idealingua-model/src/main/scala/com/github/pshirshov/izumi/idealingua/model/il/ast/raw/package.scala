package com.github.pshirshov.izumi.idealingua.model.il.ast

import com.github.pshirshov.izumi.idealingua.model.common.AbstractTypeId
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.InterfaceId

package object raw {
  type RawInterfaces = List[InterfaceId]
  type RawTuple = List[RawField]
  type RawTypes = List[AbstractTypeId]
}
