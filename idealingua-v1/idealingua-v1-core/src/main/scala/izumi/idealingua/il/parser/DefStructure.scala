package izumi.idealingua.il.parser

import izumi.idealingua.il.parser.structure.syntax.Literals
import izumi.idealingua.il.parser.structure.{Separators, aggregates, ids, kw}
import izumi.idealingua.model.il.ast.raw.defns
import izumi.idealingua.model.il.ast.raw.defns.RawAdt.Member
import izumi.idealingua.model.il.ast.raw.defns.RawEnum.EnumOp
import izumi.idealingua.model.il.ast.raw.defns.RawStructure.StructOp
import izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn.{TLDDeclared, TLDNewtype}
import izumi.idealingua.model.il.ast.raw.defns.RawTypeDef._
import izumi.idealingua.model.il.ast.raw.defns._
import izumi.idealingua.model.il.ast.raw.domains.ImportedId
import izumi.idealingua.model.il.ast.raw.typeid.ParsedId
import fastparse.NoWhitespace._
import fastparse._

class DefStructure(context: IDLParserContext) extends Separators {

  import context._

  def field[_: P]: P[RawField] = P(metaAgg.withMeta((ids.symbol | P("_" | "").map(_ => "")) ~ inline ~ ":" ~/ inline ~ ids.idGeneric))
    .map {
      case (meta, (name, tpe)) if name.isEmpty =>
        defns.RawField(tpe, None, meta)

      case (meta, (name, tpe)) =>
        defns.RawField(tpe, Some(name), meta)
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

    def struct[_: P]: P[RawStructure.Aux] = {

      P((inline ~ anyPart ~ inline).rep(sep = sepStruct))
        .map(RawStructure.Aux.apply)
    }
  }

  object SimpleStruct {
    def embed[_: P]: P[StructOp.Mix] = P((("+" ~ "++".?) | "...") ~/ (any ~ ids.identifier)).map(_.toMixinId).map(StructOp.Mix)

    def plusField[_: P]: P[StructOp.AddField] = field.map(StructOp.AddField)

    def anyPart[_: P]: P[StructOp] = P(plusField | embed)

    def sepInlineStruct[_: P]: P[Unit] = any ~ ",".? ~ any

    def simpleStruct[_: P]: P[RawSimpleStructure] = {
      P((any ~ anyPart ~ any).rep(sep = sepInlineStruct) ~ sepInlineStruct.?)
        .map(RawStructure.Aux.apply)
        .map(s => RawSimpleStructure(s.structure.concepts, s.structure.fields))
    }

  }

  def inlineStruct[_: P]: P[RawSimpleStructure] = aggregates.enclosed(SimpleStruct.simpleStruct)

  def adtOut[_: P]: P[RawAdt] = aggregates.enclosed(adt(sepAdtFreeForm))

  def aggregate[_: P]: P[Seq[RawField]] = P((inline ~ field ~ inline)
    .rep(sep = sepStruct))

  def nestedAdtMember[_: P]: P[Member.NestedDefn] = P(defMember.baseTypeMember)
    .map {
      m =>
        Member.NestedDefn(m.v)
    }


  def adtMember[_: P]: P[Member.TypeRef] = P(metaAgg.withMeta(ids.identifier ~ (inline ~ "as" ~/ (inline ~ ids.symbol)).?))
    .map {
      case (meta, (tpe, alias)) =>
        Member.TypeRef(tpe.toIndefinite, alias, meta)
    }

  def importMember[_: P]: P[ImportedId] = P(ids.symbol ~ (inline ~ "as" ~/ (inline ~ ids.symbol)).?).map {
    case (tpe, alias) =>
      ImportedId(tpe, alias)
  }

  def adt[_: P](sep: => P[Unit]): P[RawAdt] = P((nestedAdtMember | adtMember).rep(min = 1, sep = sep))
    .map(_.toList).map(RawAdt.apply)

  object Enum {
    def embed[_: P]: P[EnumOp.Extend] = P((("+" ~ "++".?) | "...") ~/ (inline ~ ids.identifier)).map(_.toEnumId).map(EnumOp.Extend)

    def enumMember[_: P]: P[EnumOp.AddMember] = P(metaAgg.withMeta(ids.symbol ~ (inline ~ "=" ~/ inline ~ defConst.constValue).?)).map {
      case (meta, (name, const)) =>
        EnumOp.AddMember(RawEnumMember(name, const.map(_.value), meta))
    }

    def minus[_: P]: P[EnumOp.RemoveMember] = P(("-" ~ "--".?) ~/ (inline ~ ids.symbol)).map(EnumOp.RemoveMember)

    def anyPart[_: P]: P[EnumOp] = P(enumMember | minus | embed)

    def enum[_: P](sep: => P[Unit]): P[RawEnum] = P(anyPart.rep(min = 1, sep = sep)).map(RawEnum.Aux.apply).map(_.structure)
  }

  def imports[_: P](sep: => P[Unit]): P[Seq[ImportedId]] = P(importMember.rep(min = 1, sep = sep))

  def mixinBlock[_: P]: P[Interface] = P(metaAgg.cblock(kw.mixin, Struct.struct)).map {
    case (c, i, v) => v.toInterface(i.toInterfaceId, c)
  }

  def dtoBlock[_: P]: P[DTO] = P(metaAgg.cblock(kw.data, Struct.struct))
    .map {
      case (c, i, v) => v.toDto(i.toDataId, c)
    }

  def stringPair[_: P]: P[(String, InterpContext)] = P(Literals.Literals.Str ~ any ~ ":" ~ any ~ ids.typeInterp)

  def foreignLinks[_: P]: P[Map[String, InterpContext]] = P(aggregates.enclosed(stringPair.rep(min = 1, sep = sepEnum))).map(_.toMap)

  def foreignBlock[_: P]: P[RawTopLevelDefn.TLDForeignType] = P(metaAgg.withMeta(kw(kw.foreign, ids.idGeneric ~ inline ~ foreignLinks)))
    .map {
      case (meta, (i, v)) =>
        ForeignType(i, v, meta)
    }
    .map(RawTopLevelDefn.TLDForeignType)

  def idBlock[_: P]: P[Identifier] = P(metaAgg.cblock(kw.id, aggregate))
    .map {
      case (c, i, v) => Identifier(i.toIdId, v.toList, c)
    }

  def aliasBlock[_: P]: P[Alias] = P(metaAgg.cstarting(kw.alias, "=" ~/ (inline ~ ids.identifier)))
    .map {
      case (c, i, v) => Alias(i.toAliasId, v.toIndefinite, c)
    }

  def declaredBlock[_: P]: P[TLDDeclared] = P(metaAgg.cstarting(kw.declared, inline))
    .map {
      case (meta, id, _) =>
        TLDDeclared(DeclaredType(id.toIndefinite, meta))
  }

  def cloneBlock[_: P]: P[TLDNewtype] = P(metaAgg.cstarting(kw.newtype, "into" ~/ (inline ~ ids.idShort ~ inline ~ aggregates.enclosed(Struct.struct).?)))
    .map {
      case (c, src, (target, struct)) =>
        NewType(target, src.toIndefinite, struct.map(_.structure), c)
    }
    .map(TLDNewtype)

  def adtFreeForm[_: P]: P[RawAdt] = P(any ~ "=" ~/ any ~ sepAdtFreeForm.? ~ any ~ adt(sepAdtFreeForm))

  def adtEnclosed[_: P]: P[RawAdt] = P(NoCut(aggregates.enclosed(adt(sepAdt) ~ sepAdt.?)) | aggregates.enclosed(adt(sepAdtFreeForm)))

  def adtBlock[_: P]: P[Adt] = P(metaAgg.cstarting(kw.adt, adtEnclosed | adtFreeForm))
    .map {
      case (c, i, v) =>
        Adt(i.toAdtId, v.alternatives, c)
    }

  def enumFreeForm[_: P]: P[RawEnum] = P(any ~ "=" ~/ any ~ sepEnumFreeForm.? ~ any ~ Enum.enum(sepEnumFreeForm))

  def enumEnclosed[_: P]: P[RawEnum] = P(NoCut(aggregates.enclosed(Enum.enum(sepEnum) ~ sepEnum.?)) | aggregates.enclosed(Enum.enum(sepEnumFreeForm)))


  def enumBlock[_: P]: P[Enumeration] = P(metaAgg.cstarting(kw.enum, enumEnclosed | enumFreeForm))
    .map {
      case (c, i, v) =>
        Enumeration(i.toEnumId, v, c)
    }
}
