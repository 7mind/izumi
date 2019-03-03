package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.RawNongenericRef

package object defns {
  type RawInterfaces = List[RawNongenericRef]
  type RawStructures = List[RawNongenericRef]
  type RawTuple = List[RawField]
}
