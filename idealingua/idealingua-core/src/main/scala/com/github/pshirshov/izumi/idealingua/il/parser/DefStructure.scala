package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.il.parser.structure.{Separators, aggregates, ids, kw}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL.{ILNewtype, ImportedId}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.RawTypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw._
import com.github.pshirshov.izumi.idealingua.model.parser.{AlgebraicType, ParsedStruct, StructOp}
import fastparse.NoWhitespace._
import fastparse._

class DefStructure(context: IDLParserContext) extends Separators {

  import context.defPositions._
  import context._

  def field[_: P]: P[RawField] = P(IP(metaAgg.withMeta((ids.symbol | P("_").map(_ => "")) ~ inline ~ ":" ~/ inline ~ ids.idGeneric)
    .map {
      case (meta, (name, tpe)) if name.isEmpty =>
        RawField(tpe, tpe.name.uncapitalize, meta)

      case (meta, (name, tpe)) =>
        RawField(tpe, name, meta)
    }))

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

    def anyPart[_: P]: P[StructOp] = P(plusField | embed)

    def sepInlineStruct[_: P]: P[Unit] = any ~ ",".? ~ any

    def simpleStruct[_: P]: P[RawSimpleStructure] = {
      P((any ~ anyPart ~ any).rep(sep = sepInlineStruct) ~ sepInlineStruct.?)
        .map(ParsedStruct.apply).map(s => RawSimpleStructure(s.structure.concepts, s.structure.fields))
    }

  }

  def inlineStruct[_: P]: P[RawSimpleStructure] = aggregates.enclosed(SimpleStruct.simpleStruct)

  def adtOut[_: P]: P[AlgebraicType] = aggregates.enclosed(adt(sepAdtFreeForm))

  def aggregate[_: P]: P[Seq[RawField]] = P((inline ~ field ~ inline)
    .rep(sep = sepStruct))

  def adtMember[_: P]: P[RawAdtMember] = P(metaAgg.withMeta(ids.identifier ~ (inline ~ "as" ~/ (inline ~ ids.symbol)).?)).map {
    case (meta, (tpe, alias)) =>
      RawAdtMember(tpe.toTypeId, alias, meta)
  }

  def importMember[_: P]: P[ImportedId] = P(ids.symbol ~ (inline ~ "as" ~/ (inline ~ ids.symbol)).?).map {
    case (tpe, alias) =>
      ImportedId(tpe, alias)
  }

  def adt[_: P](sep: => P[Unit]): P[AlgebraicType] = P(adtMember.rep(min = 1, sep = sep))
    .map(_.toList).map(AlgebraicType)

  def enum[_: P](sep: => P[Unit]): P[Seq[String]] = P(ids.symbol.rep(min = 1, sep = sep))

  def imports[_: P](sep: => P[Unit]): P[Seq[ImportedId]] = P(importMember.rep(min = 1, sep = sep))

  def mixinBlock[_: P]: P[Interface] = P(IP(metaAgg.cblock(kw.mixin, Struct.struct)
    .map {
      case (c, i, v) => v.toInterface(i.toInterfaceId, c)
    }))

  def dtoBlock[_: P]: P[DTO] = P(IP(metaAgg.cblock(kw.data, Struct.struct)
    .map {
      case (c, i, v) => v.toDto(i.toDataId, c)
    }))

  def idBlock[_: P]: P[Identifier] = P(IP(metaAgg.cblock(kw.id, aggregate)
    .map {
      case (c, i, v) => Identifier(i.toIdId, v.toList, c)
    }))

  def aliasBlock[_: P]: P[Alias] = P(IP(metaAgg.cstarting(kw.alias, "=" ~/ (inline ~ ids.identifier))
    .map {
      case (c, i, v) => Alias(i.toAliasId, v.toTypeId, c)
    }))

  def cloneBlock[_: P]: P[ILNewtype] = P(IP(metaAgg.cstarting(kw.newtype, "into" ~/ (inline ~ ids.idShort ~ inline ~ aggregates.enclosed(Struct.struct).?))
    .map {
      case (c, src, (target, struct)) =>
        NewType(target, src.toTypeId, struct.map(_.structure), c)
    }))
    .map(ILNewtype)

  def adtFreeForm[_: P]: P[AlgebraicType] = P(any ~ "=" ~/ any ~ sepAdtFreeForm.? ~ any ~ adt(sepAdtFreeForm))

  def adtEnclosed[_: P]: P[AlgebraicType] = P(NoCut(aggregates.enclosed(adt(sepAdt) ~ sepAdt.?)) | aggregates.enclosed(adt(sepAdtFreeForm)))

  def adtBlock[_: P]: P[Adt] = P(IP(metaAgg.cstarting(kw.adt, adtEnclosed | adtFreeForm)
    .map {
      case (c, i, v) =>
        Adt(i.toAdtId, v.alternatives, c)
    }))

  def enumFreeForm[_: P]: P[Seq[String]] = P(any ~ "=" ~/ any ~ sepEnumFreeForm.? ~ any ~ enum(sepEnumFreeForm))

  def enumEnclosed[_: P]: P[Seq[String]] = P(NoCut(aggregates.enclosed(enum(sepEnum) ~ sepEnum.?)) | aggregates.enclosed(enum(sepEnumFreeForm)))


  def enumBlock[_: P]: P[Enumeration] = P(IP(metaAgg.cstarting(kw.enum, enumEnclosed | enumFreeForm)
    .map {
      case (c, i, v) =>
        Enumeration(i.toEnumId, v.toList, c)
    }))
}
