package izumi.idealingua.model.il.ast

import izumi.idealingua.model.common.StructureId
import izumi.idealingua.model.common.TypeId.InterfaceId

package object typed {
  type Interfaces = List[InterfaceId]
  type Structures = List[StructureId]
  type Tuple = List[Field]
  type IdTuple = List[IdField]
}
