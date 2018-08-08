package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.il.parser.model.{AlgebraicType, ParsedStruct, StructOp}
import com.github.pshirshov.izumi.idealingua.il.parser.structure.{Separators, aggregates, ids, kw}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL.{ILDef, ILNewtype, ImportedId}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.RawTypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.{ParsedId, RawAdtMember, RawField, RawSimpleStructure}
import fastparse.all._

trait DefStructure extends Separators {
  final val field = P((ids.symbol | P("_").map(_ => "")) ~ inline ~ ":" ~/ inline ~ ids.idGeneric)
    .map {
      case (name, tpe) if name.isEmpty =>
        RawField(tpe, tpe.name.uncapitalize)

      case (name, tpe) =>
        RawField(tpe, name)
    }


  final val struct = {
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


  final val simpleStruct = {
    val sepInline = any
    val sepInlineStruct = any ~ ",".? ~ any

    val embed = P((("+" ~ "++".?) | "...") ~/ sepInline ~ ids.identifier).map(_.toMixinId).map(StructOp.Mix)
    val plusField = field.map(StructOp.AddField)
    val anyPart = P(plusField | embed)

    P((sepInline ~ anyPart ~ sepInline).rep(sep = sepInlineStruct))
      .map(ParsedStruct.apply).map(s => RawSimpleStructure(s.structure.concepts, s.structure.fields))
  }

  final val inlineStruct = aggregates.enclosed(DefStructure.simpleStruct)

  final val adtOut = aggregates.enclosed(DefStructure.adt(sepAdt))

  final val aggregate = P((inline ~ field ~ inline)
    .rep(sep = sepStruct))

  final val adtMember = P(ids.identifier ~ (inline ~ "as" ~/ inline ~ ids.symbol).?).map {
    case (tpe, alias) =>
      RawAdtMember(tpe.toTypeId, alias)
  }

  final val importMember = P(ids.symbol ~ (inline ~ "as" ~/ inline ~ ids.symbol).?).map {
    case (tpe, alias) =>
      ImportedId(tpe, alias)
  }

  final def adt(sep: Parser[Unit]): Parser[AlgebraicType] = P(adtMember.rep(min = 1, sep = sep))
    .map(_.toList).map(AlgebraicType)

  final def enum(sep: Parser[Unit]): Parser[Seq[String]] = P(ids.symbol.rep(min = 1, sep = sep))

  final def imports(sep: Parser[Unit]): Parser[Seq[ImportedId]] = P(importMember.rep(min = 1, sep = sep))

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

}

object DefStructure extends DefStructure {
}
