package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.RawTypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.Service
import fastparse.all._


trait DefMember extends Aggregates {
  final val inclusion = kw(kw.include, sym.String)
    .map(v => ILInclude(v))

  final val mixinBlock = aggregates.cblock(kw.mixin, DefStructure.struct)
    .map {
      case (c, i, v)  => ILDef(v.toInterface(i.toInterfaceId, c))
    }

  final val dtoBlock = aggregates.cblock(kw.data, DefStructure.struct)
    .map {
      case (c, i, v)  => ILDef(v.toDto(i.toDataId, c))
    }

  final val idBlock = aggregates.cblock(kw.id, DefStructure.aggregate)
    .map {
      case (c, i, v)  => ILDef(Identifier(i.toIdId, v.toList, c))
    }

  final val aliasBlock = aggregates.cstarting(kw.alias, "=" ~/ inline ~ ids.identifier)
    .map {
      case (c, i, v)  => ILDef(Alias(i.toAliasId, v.toTypeId, c))
    }

  final val cloneBlock = aggregates.cstarting(kw.newtype, "into" ~/ inline ~ ids.idShort ~ inline ~ aggregates.enclosed(DefStructure.struct).?)
    .map {
      case (c, src, (target, struct))  =>
        ILNewtype(NewType(target, src.toTypeId, struct.map(_.structure), c))
    }

  final val adtBlock = aggregates.cstarting(kw.adt,
    aggregates.enclosed(DefStructure.adt(sepAdt))
      | (any ~ "=" ~/ sepAdt ~ DefStructure.adt(sepAdt))
  )
    .map {
      case (c, i, v) =>
        ILDef(Adt(i.toAdtId, v.alternatives, c))
    }

  final val enumBlock = aggregates.cstarting(kw.enum
    , aggregates.enclosed(DefStructure.enum(sepEnum)) |
      (any ~ "=" ~/ sepEnum ~ DefStructure.enum(sepEnum))
  )
    .map {
      case (c, i, v) => ILDef(Enumeration(i.toEnumId, v.toList, c))
    }

  final val serviceBlock = aggregates.cblock(kw.service, DefService.methods)
    .map {
      case (c, i, v) => ILService(Service(i.toServiceId, v.toList, c))
    }

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
