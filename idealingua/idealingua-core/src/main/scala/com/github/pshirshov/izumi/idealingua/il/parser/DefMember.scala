package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL._
import fastparse._


class DefMember(context: IDLParserContext) extends Aggregates {
  def inclusion[_: P]: P[ILInclude] = kw(kw.include, sym.String)
    .map(v => ILInclude(v))

  def typeMember[_: P]: P[ILDef] = (
    DefStructure.enumBlock |
    DefStructure.adtBlock |
    DefStructure.aliasBlock |
    DefStructure.idBlock |
    DefStructure.mixinBlock |
    DefStructure.dtoBlock
    )
    .map(ILDef)

  def anyMember[_: P]: P[Val] = typeMember | (
    DefStructure.cloneBlock |
      DefService.serviceBlock |
      DefBuzzer.buzzerBlock |
      DefStreams.streamsBlock |
      DefConst.constBlock |
      inclusion
    )

}

