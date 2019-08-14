package izumi.idealingua.model.il.ast.raw

import izumi.idealingua.model.common.IndefiniteMixin
import izumi.idealingua.model.common.TypeId.InterfaceId

package object defns {
  type RawInterfaces = List[InterfaceId]
  type RawStructures = List[IndefiniteMixin]
  type RawTuple = List[RawField]
}
