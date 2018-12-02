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
  def field[_: P]: P[RawField] = P((ids.symbol | P("_").map(_ => "")) ~ inline ~ ":" ~/ inline ~ ids.idGeneric)
    .map {
      case (name, tpe) if name.isEmpty =>
        RawField(tpe, tpe.name.uncapitalize)

      case (name, tpe) =>
        RawField(tpe, name)
    }

  object Struct {
    def plus[_: P]: P[StructOp.Extend] = P(("&" ~ "&&".?) ~/ (inline ~ ids.identifier)).map(_.toParentId).map(StructOp.Extend)

    def embed[_: P]: P[StructOp.Mix] = P((("+" ~ "++".?) | "...") ~/ (inline ~ ids.identifier)).map(_.toMixinId).map(StructOp.Mix)

    def minus[_: P]: P[StructOp] = P(("-" ~ "--".?) ~/ (inline ~ (field | ids.identifier))).map {
      case v: RawField =>
        StructOp.RemoveField(v)
      case i: ParsedId =>
        StructOp.Drop(i.toMixinId)
    }

    def plusField[_: P]: P[StructOp.AddField] = field.map(StructOp.AddField)

    def anyPart[_: P]: P[StructOp] = P(plusField | plus | embed | minus)

    def struct[_: P]: P[ParsedStruct] = {

      P((inline ~ anyPart ~ inline).rep(sep = sepStruct))
        .map(ParsedStruct.apply)
    }
  }

  object SimpleStruct {
    def embed[_: P]: P[StructOp.Mix] = P((("+" ~ "++".?) | "...") ~/ (any ~ ids.identifier)).map(_.toMixinId).map(StructOp.Mix)

    def plusField[_: P]: P[StructOp.AddField] = field.map(StructOp.AddField)

    def anyPart [_: P]: P[StructOp] = P(plusField | embed)

    def sepInlineStruct [_: P]: P[Unit] = any ~ ",".? ~ any

    def simpleStruct[_: P]: P[RawSimpleStructure] = {


      P((any ~ anyPart ~ any).rep(sep = sepInlineStruct))
        .map(ParsedStruct.apply).map(s => RawSimpleStructure(s.structure.concepts, s.structure.fields))
    }

  }

  def inlineStruct[_: P]: P[RawSimpleStructure] = aggregates.enclosed(DefStructure.SimpleStruct.simpleStruct)

  def adtOut[_: P]: P[AlgebraicType] = aggregates.enclosed(DefStructure.adt(sepAdt))

  def aggregate[_: P]: P[Seq[RawField]] = P((inline ~ field ~ inline)
    .rep(sep = sepStruct))

  def adtMember[_: P]: P[RawAdtMember] = P(ids.identifier ~ (inline ~ "as" ~/ (inline ~ ids.symbol)).?).map {
    case (tpe, alias) =>
      RawAdtMember(tpe.toTypeId, alias)
  }

  def importMember[_: P]: P[ImportedId] = P(ids.symbol ~ (inline ~ "as" ~/ (inline ~ ids.symbol)).?).map {
    case (tpe, alias) =>
      ImportedId(tpe, alias)
  }

  def adt[_: P](sep: => P[Unit]): P[AlgebraicType] = P(adtMember.rep(min = 1, sep = sep))
    .map(_.toList).map(AlgebraicType)

  def enum[_: P](sep: => P[Unit]): P[Seq[String]] = P(ids.symbol.rep(min = 1, sep = sep))

  def imports[_: P](sep: => P[Unit]): P[Seq[ImportedId]] = P(importMember.rep(min = 1, sep = sep))

  def mixinBlock[_: P]: P[ILDef] = aggregates.cblock(kw.mixin, DefStructure.Struct.struct)
    .map {
      case (c, i, v) => ILDef(v.toInterface(i.toInterfaceId, c))
    }

  def dtoBlock[_: P]: P[ILDef] = aggregates.cblock(kw.data, DefStructure.Struct.struct)
    .map {
      case (c, i, v) => ILDef(v.toDto(i.toDataId, c))
    }

  def idBlock[_: P]: P[ILDef] = aggregates.cblock(kw.id, DefStructure.aggregate)
    .map {
      case (c, i, v) => ILDef(Identifier(i.toIdId, v.toList, c))
    }

  def aliasBlock[_: P]: P[ILDef] = aggregates.cstarting(kw.alias, "=" ~/ (inline ~ ids.identifier))
    .map {
      case (c, i, v) => ILDef(Alias(i.toAliasId, v.toTypeId, c))
    }

  def cloneBlock[_: P]: P[ILNewtype] = aggregates.cstarting(kw.newtype, "into" ~/ (inline ~ ids.idShort ~ inline ~ aggregates.enclosed(DefStructure.Struct.struct).?))
    .map {
      case (c, src, (target, struct)) =>
        ILNewtype(NewType(target, src.toTypeId, struct.map(_.structure), c))
    }

  def adtBlock[_: P]: P[ILDef] = aggregates.cstarting(kw.adt, P(aggregates.enclosed(DefStructure.adt(sepAdt)) | (any ~ "=" ~/ (sepAdt ~ DefStructure.adt(sepAdt)))))
    .map {
      case (c, i, v) =>
        ILDef(Adt(i.toAdtId, v.alternatives, c))
    }

  def enumBlock[_: P]: P[ILDef] = aggregates.cstarting(kw.enum
    , aggregates.enclosed(DefStructure.enum(sepEnum)) |
      (any ~ "=" ~/ sepEnum ~ DefStructure.enum(sepEnum))
  )
    .map {
      case (c, i, v) =>
        ILDef(Enumeration(i.toEnumId, v.toList, c))
    }

}

object DefStructure extends DefStructure {
}
