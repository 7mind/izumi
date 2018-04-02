package com.github.pshirshov.izumi.idealingua.model.il.ast

import com.github.pshirshov.izumi.idealingua.model.common.AbstractTypeId
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.InterfaceId

package object raw {
  type Composite = List[InterfaceId]
  type Aggregate = List[Field]
  type Types = List[AbstractTypeId]
}
