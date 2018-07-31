package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.RawTypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.Service
import fastparse.all._


trait DefMember extends Aggregates {
  final val inclusion = kw(kw.include, sym.String)
    .map(v => ILInclude(v))

  final val mixinBlock = aggregates.block(kw.mixin, DefStructure.struct)
    .map(v => ILDef(v._2.toInterface(v._1.toInterfaceId)))

  final val dtoBlock = aggregates.block(kw.data, DefStructure.struct)
    .map(v => ILDef(v._2.toDto(v._1.toDataId)))

  final val idBlock = aggregates.block(kw.id, DefStructure.aggregate)
    .map(v => ILDef(Identifier(v._1.toIdId, v._2.toList)))

  final val aliasBlock = aggregates.starting(kw.alias, "=" ~/ inline ~ ids.identifier)
    .map(v => ILDef(Alias(v._1.toAliasId, v._2.toTypeId)))

  final val cloneBlock = aggregates.starting(kw.newtype, "into" ~/ inline ~ ids.idShort ~ inline ~ aggregates.enclosed(DefStructure.struct).?)
    .map {
      case (src, (target, struct)) =>
        ILNewtype(NewType(target, src.toTypeId, struct.map(_.structure)))
    }

  final val adtBlock = aggregates.starting(kw.adt,
    aggregates.enclosed(DefStructure.adt(sepAdt))
      | (any ~ "=" ~/ sepAdt ~ DefStructure.adt(sepAdt))
  )
    .map(v => ILDef(Adt(v._1.toAdtId, v._2.alternatives)))

  final val enumBlock = aggregates.starting(kw.enum
    , aggregates.enclosed(DefStructure.enum(sepEnum)) |
      (any ~ "=" ~/ sepEnum ~ DefStructure.enum(sepEnum))
  )
    .map(v => ILDef(Enumeration(v._1.toEnumId, v._2.toList)))

  final val serviceBlock = aggregates.block(kw.service, DefService.methods)
    .map(v => ILService(Service(v._1.toServiceId, v._2.toList)))

  final val anyMember: Parser[Val] = enumBlock |
    adtBlock |
    aliasBlock |
    cloneBlock |
    idBlock |
    mixinBlock |
    dtoBlock |
    serviceBlock |
    inclusion

}

object DefMember extends DefMember {
}
