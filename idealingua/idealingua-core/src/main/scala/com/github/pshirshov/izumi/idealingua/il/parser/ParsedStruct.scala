package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{DTOId, InterfaceId}
import com.github.pshirshov.izumi.idealingua.model.il.parsing.ILAstParsed
import com.github.pshirshov.izumi.idealingua.model.il.parsing.ILAstParsed.{DTO, Field, Interface}

case class ParsedStruct(inherited: List[InterfaceId], mixed: List[InterfaceId], removed: List[InterfaceId], fields: List[Field], removedFields: List[Field]) {
  def toInterface(id: InterfaceId): ILAstParsed.Interface = {
    Interface(id, fields, inherited, mixed)
  }

  def toDto(id: DTOId): ILAstParsed.DTO = {
    DTO(id, fields, inherited, mixed)
  }
}

object ParsedStruct {
  def apply(v: Seq[StructOp]): ParsedStruct = {
    import StructOp._
    ParsedStruct(
      v.collect({ case Extend(i) => i }).toList
      , v.collect({ case Mix(i) => i }).toList
      , v.collect({ case Drop(i) => i }).toList
      , v.collect({ case AddField(i) => i }).toList
      , v.collect({ case RemoveField(i) => i }).toList
    )
  }
}
