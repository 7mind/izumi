package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL._
import fastparse._


class DefMember(context: IDLParserContext) extends Aggregates {

  import context._

  def inclusion[_: P]: P[ILInclude] = kw(kw.include, sym.String)
    .map(v => ILInclude(v))

  def typeMember[_: P]: P[ILDef] = (
    defStructure.enumBlock |
      defStructure.adtBlock |
      defStructure.aliasBlock |
      defStructure.idBlock |
      defStructure.mixinBlock |
      defStructure.dtoBlock
    )
    .map(ILDef)

  def anyMember[_: P]: P[Val] = typeMember | (
    defStructure.cloneBlock |
      defService.serviceBlock |
      defBuzzer.buzzerBlock |
      defStreams.streamsBlock |
      defConst.constBlock |
      defStructure.foreignBlock |
      inclusion
    )

}

