package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{DTOId, InterfaceId}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.RawTypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw._

final case class ParsedStruct(structure: RawStructure) {
  def toInterface(id: InterfaceId): RawTypeDef.Interface = {
    Interface(id, structure)
  }

  def toDto(id: DTOId): RawTypeDef.DTO = {
    DTO(id, structure)
  }
}

object ParsedStruct {
  def apply(v: Seq[StructOp]): ParsedStruct = {
    import StructOp._
    ParsedStruct(RawStructure(
      v.collect({ case Extend(i) => i }).toList
      , v.collect({ case Mix(i) => i }).toList
      , v.collect({ case Drop(i) => i }).toList
      , v.collect({ case AddField(i) => i }).toList
      , v.collect({ case RemoveField(i) => i }).toList
    ))
  }
}
