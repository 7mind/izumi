package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.il.parser.structure.{Separators, aggregates, ids, kw}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL.{ILDef, ILNewtype, ImportedId}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.RawTypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.{ParsedId, RawAdtMember, RawField, RawSimpleStructure}
import com.github.pshirshov.izumi.idealingua.model.parser.{AlgebraicType, ParsedStruct, StructOp}
import fastparse._
import fastparse.NoWhitespace._

trait DefStructure extends Separators {
  final def field[_:P]: P[RawField] = P((ids.symbol | P("_").map(_ => "")) ~ inline ~ ":" ~/ inline ~ ids.idGeneric)
    .map {
      case (name, tpe) if name.isEmpty =>
        RawField(tpe, tpe.name.uncapitalize)

      case (name, tpe) =>
        RawField(tpe, name)
    }


  final def struct[_:P]: P[ParsedStruct] = {
    val sepEntry = sepStruct
    val sepInline = inline

    val plus = P(("&" ~ "&&".?) ~/ sepInline ~ ids.identifier).map(_.toParentId).map(StructOp.Extend)
    val embed = P((("+" ~ "++".?) | "...") ~/ sepInline ~ ids.identifier).map(_.toMixinId).map(StructOp.Mix)
    val minus = P(("-" ~ "--".?) ~/ sepInline ~ (field | ids.identifier)).map {
      case v: RawField =>
        StructOp.RemoveField(v)
      case i: ParsedId =>
        StructOp.Drop(i.toMixinId)
    }
    val plusField = field.map(StructOp.AddField)

    val anyPart = P(plusField | plus | embed | minus)

    P((sepInline ~ anyPart ~ sepInline).rep(sep = sepEntry))
      .map(ParsedStruct.apply)
  }


  final def simpleStruct[_:P]: P[RawSimpleStructure] = {
    val sepInline = any
    val sepInlineStruct = any ~ ",".? ~ any

    val embed = P((("+" ~ "++".?) | "...") ~/ sepInline ~ ids.identifier).map(_.toMixinId).map(StructOp.Mix)
    val plusField = field.map(StructOp.AddField)
    val anyPart = P(plusField | embed)

    P((sepInline ~ anyPart ~ sepInline).rep(sep = sepInlineStruct))
      .map(ParsedStruct.apply).map(s => RawSimpleStructure(s.structure.concepts, s.structure.fields))
  }

  final def inlineStruct[_:P]: P[RawSimpleStructure] = aggregates.enclosed(DefStructure.simpleStruct)

  final def adtOut[_:P]: P[AlgebraicType] = aggregates.enclosed(DefStructure.adt(sepAdt))

  final def aggregate[_:P]: P[Seq[RawField]] = P((inline ~ field ~ inline)
    .rep(sep = sepStruct))

  final def adtMember[_:P]: P[RawAdtMember] = P(ids.identifier ~ (inline ~ "as" ~/ inline ~ ids.symbol).?).map {
    case (tpe, alias) =>
      RawAdtMember(tpe.toTypeId, alias)
  }

  final def importMember[_:P]: P[ImportedId] = P(ids.symbol ~ (inline ~ "as" ~/ inline ~ ids.symbol).?).map {
    case (tpe, alias) =>
      ImportedId(tpe, alias)
  }

  final def adt(sep: P[Unit])(implicit p: P[_]): P[AlgebraicType] = P(adtMember.rep(min = 1, sep = sep))
    .map(_.toList).map(AlgebraicType)

  final def enum(sep: P[Unit])(implicit p: P[_]): P[Seq[String]] = P(ids.symbol.rep(min = 1, sep = sep))

  final def imports(sep: P[Unit])(implicit p: P[_]): P[Seq[ImportedId]] = P(importMember.rep(min = 1, sep = sep))

  final def mixinBlock[_:P]: P[ILDef] = aggregates.cblock(kw.mixin, DefStructure.struct)
    .map {
      case (c, i, v)  => ILDef(v.toInterface(i.toInterfaceId, c))
    }

  final def dtoBlock[_:P]: P[ILDef] = aggregates.cblock(kw.data, DefStructure.struct)
    .map {
      case (c, i, v)  => ILDef(v.toDto(i.toDataId, c))
    }

  final def idBlock[_:P]: P[ILDef] = aggregates.cblock(kw.id, DefStructure.aggregate)
    .map {
      case (c, i, v)  => ILDef(Identifier(i.toIdId, v.toList, c))
    }

  final def aliasBlock[_:P]: P[ILDef] = aggregates.cstarting(kw.alias, "=" ~/ inline ~ ids.identifier)
    .map {
      case (c, i, v)  => ILDef(Alias(i.toAliasId, v.toTypeId, c))
    }

  final def cloneBlock[_:P]: P[ILNewtype] = aggregates.cstarting(kw.newtype, "into" ~/ inline ~ ids.idShort ~ inline ~ aggregates.enclosed(DefStructure.struct).?)
    .map {
      case (c, src, (target, struct))  =>
        ILNewtype(NewType(target, src.toTypeId, struct.map(_.structure), c))
    }

  final def adtBlock[_:P]: P[ILDef] = aggregates.cstarting(kw.adt,
    aggregates.enclosed(DefStructure.adt(sepAdt))
      | (any ~ "=" ~/ sepAdt ~ DefStructure.adt(sepAdt))
  )
    .map {
      case (c, i, v) =>
        ILDef(Adt(i.toAdtId, v.alternatives, c))
    }

  final def enumBlock[_:P]: P[ILDef] = aggregates.cstarting(kw.enum
    , aggregates.enclosed(DefStructure.enum(sepEnum)) |
      (any ~ "=" ~/ sepEnum ~ DefStructure.enum(sepEnum))
  )
    .map {
      case (c, i, v) => ILDef(Enumeration(i.toEnumId, v.toList, c))
    }

}

object DefStructure extends DefStructure {
}
