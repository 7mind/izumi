package izumi.idealingua.il.parser

import izumi.idealingua.il.parser.structure._
import izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn
import izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn.TLDBaseType
import izumi.idealingua.model.il.ast.raw.models.{Inclusion, ModelMember}
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

  def typeMember[_: P]: P[RawTopLevelDefn.TypeDefn] = P(
    defStructure.foreignBlock |
      defStructure.cloneBlock |
      defStructure.declaredBlock
  )

  def otherMember[_: P]: P[RawTopLevelDefn] = P(
    defService.serviceBlock |
      defBuzzer.buzzerBlock |
      defStreams.streamsBlock |
      defConst.constBlock
  )

  def topLevelDefn[_: P]: P[ModelMember] = P(
    baseTypeMember |
      typeMember |
      otherMember
  ).map(ModelMember.MMTopLevelDefn)

  def anyMember[_: P]: P[ModelMember] = P(topLevelDefn | inclusion.map(ModelMember.MMInclusion))

}

