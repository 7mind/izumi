package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.RawRef

package object defns {
  type RawInterfaces = List[RawRef]
  type RawStructures = List[RawRef]
  type RawTuple = List[RawField]
}
