package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn.{TLDBaseType, TLDDeclared}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.models.{Inclusion, ModelMember}
import fastparse._


class DefMember(context: IDLParserContext) extends Aggregates {

  import context._

  def inclusion[_: P]: P[Inclusion] = kw(kw.include, sym.String)
    .map(v => Inclusion(v))

  def baseTypeMember[_: P]: P[TLDBaseType] = P(
    defStructure.enumBlock |
      defStructure.adtBlock |
      defStructure.aliasBlock |
      defStructure.idBlock |
      defStructure.mixinBlock |
      defStructure.dtoBlock
  )
    .map(TLDBaseType)

  private def typeMember[_: P]: P[RawTopLevelDefn.TypeDefn] = P(
    defStructure.foreignBlock |
      defStructure.cloneBlock |
      defStructure.instanceBlock |
      defStructure.templateBlock
  )

  private def interfaceMember[_: P]: P[RawTopLevelDefn.NamedDefn] = P(
    defService.serviceBlock |
      defBuzzer.buzzerBlock |
      defStreams.streamsBlock
  )

  private def namedMember[_: P]: P[RawTopLevelDefn.NamedDefn] = P(
    baseTypeMember | typeMember | interfaceMember
  )


  private def otherMember[_: P]: P[RawTopLevelDefn] = P(
    defConst.constBlock |
      declaredBlock
  )

  private def declaredBlock[_: P]: P[TLDDeclared] = P(kw(kw.declared, namedMember | inBraces(namedMember)))
    .map {
      member =>
        TLDDeclared(member)
    }


  def topLevelDefn[_: P]: P[ModelMember] = P(
    namedMember |
      otherMember
  ).map(ModelMember.MMTopLevelDefn)

  def anyMember[_: P]: P[ModelMember] = P(topLevelDefn | inclusion.map(ModelMember.MMInclusion))

}

