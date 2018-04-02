package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{DTOId, InterfaceId}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.ILAstParsed._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw._

case class ParsedStruct(structure: Structure) {
  def toInterface(id: InterfaceId): ILAstParsed.Interface = {
    Interface(id, structure)
  }

  def toDto(id: DTOId): ILAstParsed.DTO = {
    DTO(id, structure)
  }
}

object ParsedStruct {
  def apply(v: Seq[StructOp]): ParsedStruct = {
    import StructOp._
    ParsedStruct(Structure(
      v.collect({ case Extend(i) => i }).toList
      , v.collect({ case Mix(i) => i }).toList
      , v.collect({ case Drop(i) => i }).toList
      , v.collect({ case AddField(i) => i }).toList
      , v.collect({ case RemoveField(i) => i }).toList
    ))
  }
}
