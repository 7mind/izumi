package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure.syntax.Literals
import com.github.pshirshov.izumi.idealingua.il.parser.structure.{Separators, aggregates, ids, kw}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawAdt.Member
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawClone.CloneOp
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawEnum.EnumOp
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawStructure.StructOp
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn.{TLDInstance, TLDNewtype}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.domains.ImportedId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.RawNongenericRef
import fastparse.NoWhitespace._
import fastparse._

class DefStructure(context: IDLParserContext) extends Separators {

  import context._

  def field[_: P]: P[RawField] = P(metaAgg.withMeta(ids.fieldName ~ inline ~ ":" ~/ inline ~ ids.typeReference))
    .map {
      case (meta, (name, tpe)) if name.isEmpty =>
        defns.RawField(tpe, None, meta)

      case (meta, (name, tpe)) =>
        defns.RawField(tpe, Some(name), meta)
    }

  object Struct {
    def plus[_: P]: P[StructOp.Extend] = P(("&" ~ "&&".?) ~/ (inline ~ ids.parentStruct)).map(StructOp.Extend)

    def embed[_: P]: P[StructOp.Mix] = P((("+" ~ "++".?) | "...") ~/ (inline ~ ids.parentStruct)).map(StructOp.Mix)

    def minus[_: P]: P[StructOp] = P(("-" ~ "--".?) ~/ (inline ~ (field | ids.parentStruct))).map {
      case v: RawField =>
        StructOp.RemoveField(v)
      case i: RawNongenericRef =>
        StructOp.Drop(i)
    }

    def plusField[_: P]: P[StructOp.AddField] = field.map(StructOp.AddField)

    def anyPart[_: P]: P[StructOp] = P(plusField | plus | embed | minus)

    def struct[_: P]: P[RawStructure.Aux] = {

      P((inline ~ anyPart ~ inline).rep(sep = sepStruct))
        .map(RawStructure.Aux.apply)
    }
  }

  object Clone {
    def plus[_: P]: P[CloneOp.Extend] = P(("&" ~ "&&".?) ~/ (inline ~ ids.parentStruct)).map(CloneOp.Extend)

    def minusParent[_: P]: P[CloneOp.DropParent] = P(("!" ~ "!!".?) ~/ (inline ~ ids.parentStruct)).map(CloneOp.DropParent)

    def embed[_: P]: P[CloneOp.Mix] = P((("+" ~ "++".?) | "...") ~/ (inline ~ ids.parentStruct)).map(CloneOp.Mix)

    def minusConcept[_: P]: P[CloneOp] = P(("-" ~ "--".?) ~/ (inline ~ (field | ids.parentStruct))).map {
      case v: RawField =>
        CloneOp.RemoveField(v)
      case i: RawNongenericRef =>
        CloneOp.DropConcept(i)
    }

    def adtBranchPlus[_: P]: P[CloneOp.AddBranch] = P("|" ~/ (inline ~ adtAnyMember)).map(CloneOp.AddBranch)

    def adtBranchMinus[_: P]: P[CloneOp.RemoveBranch] = P("\\" ~/ (inline ~ ids.declaredTypeName)).map(CloneOp.RemoveBranch)

    def plusField[_: P]: P[CloneOp.AddField] = field.map(CloneOp.AddField)

    def anyPart[_: P]: P[CloneOp] = P(plusField | plus | embed | minusConcept | minusParent | adtBranchPlus | adtBranchMinus)

    def struct[_: P]: P[RawClone] = {

      P((inline ~ anyPart ~ inline).rep(sep = sepStruct))
        .map(RawClone.apply)
    }
  }


  object SimpleStruct {
    def embed[_: P]: P[StructOp.Mix] = P((("+" ~ "++".?) | "...") ~/ (any ~ ids.parentStruct)).map(StructOp.Mix)

    def plusField[_: P]: P[StructOp.AddField] = field.map(StructOp.AddField)

    def anyPart[_: P]: P[StructOp] = P(plusField | embed)

    def sepInlineStruct[_: P]: P[Unit] = any ~ ",".? ~ any

    def simpleStruct[_: P]: P[RawSimpleStructure] = {
      P((any ~ anyPart ~ any).rep(sep = sepInlineStruct) ~ sepInlineStruct.?)
        .map {
          case s =>
            val ops = RawStructure.Aux(s)
            RawSimpleStructure(ops.structure.concepts, ops.structure.fields)
        }
    }

  }

  def inlineStruct[_: P]: P[RawSimpleStructure] = aggregates.enclosed(SimpleStruct.simpleStruct)

  def aggregate[_: P]: P[Seq[RawField]] = P((inline ~ field ~ inline)
    .rep(sep = sepStruct))

  def adtMemberNested[_: P]: P[Member.NestedDefn] = P(defMember.baseTypeMember)
    .map {
      m =>
        Member.NestedDefn(m.defn)
    }


  def adtMemberTypeRef[_: P]: P[Member.TypeRef] = P(metaAgg.withMeta(ids.typeReference ~ (inline ~ "as" ~/ (inline ~ ids.adtMemberName)).?))
    .map {
      case (meta, (tpe, alias)) =>
        Member.TypeRef(tpe, alias, meta)
    }

  def adtAnyMember[_: P]: P[Member] = P(adtMemberNested | adtMemberTypeRef)

  def importMember[_: P]: P[ImportedId] = P(metaAgg.withMeta(ids.importedName ~ (inline ~ "as" ~/ (inline ~ ids.importedName)).?)).map {
    case (meta, (tpe, alias)) =>
      ImportedId(tpe, alias, meta)
  }

  def adt[_: P](sep: => P[Unit]): P[RawAdt] = P(adtAnyMember.rep(min = 0, sep = sep))
    .map(_.toList).map(RawAdt.apply)

  object Enum {
    def embed[_: P]: P[EnumOp.Extend] = P((("+" ~ "++".?) | "...") ~/ (inline ~ ids.parentEnum)).map(EnumOp.Extend)

    def enumMember[_: P]: P[EnumOp.AddMember] = P(metaAgg.withMeta(ids.enumMemberName ~ (inline ~ "=" ~/ inline ~ defConst.constValue).?)).map {
      case (meta, (name, const)) =>
        EnumOp.AddMember(RawEnumMember(name, const.map(_.value), meta))
    }

    def minus[_: P]: P[EnumOp.RemoveMember] = P(("-" ~ "--".?) ~/ (inline ~ ids.removedParentName)).map(EnumOp.RemoveMember)

    def anyPart[_: P]: P[EnumOp] = P(enumMember | minus | embed)

    def enum[_: P](sep: => P[Unit]): P[RawEnum] = P(anyPart.rep(min = 0, sep = sep)).map(RawEnum.Aux.apply).map(_.structure)
  }

  def imports[_: P](sep: => P[Unit]): P[Seq[ImportedId]] = P(importMember.rep(min = 1, sep = sep))

  def mixinBlock[_: P]: P[Interface] = P(metaAgg.cblock(kw.mixin, Struct.struct)).map {
    case (c, i, v) => v.toInterface(i, c)
  }

  def dtoBlock[_: P]: P[DTO] = P(metaAgg.cblock(kw.data, Struct.struct))
    .map {
      case (c, i, v) => v.toDto(i, c)
    }

  def stringPair[_: P]: P[(String, InterpContext)] = P(Literals.Literals.Str ~ any ~ ":" ~ any ~ ids.typeInterp)

  def foreignLinks[_: P]: P[Map[String, InterpContext]] = P(aggregates.enclosed(stringPair.rep(min = 1, sep = sepEnum))).map(_.toMap)

  def foreignBlock[_: P]: P[RawTopLevelDefn.TLDForeignType] = P(metaAgg.withMeta(kw(kw.foreign, ids.typeReferenceLocal ~ inline ~ foreignLinks)))
    .map {
      case (meta, (i, v)) =>
        ForeignType(i, v, meta)
    }
    .map(RawTopLevelDefn.TLDForeignType)

  def idBlock[_: P]: P[Identifier] = P(metaAgg.cblock(kw.id, aggregate))
    .map {
      case (c, i, v) => Identifier(i, v.toList, c)
    }

  def aliasBlock[_: P]: P[Alias] = P(metaAgg.cstarting(kw.alias, "=" ~/ (inline ~ ids.typeReference)))
    .map {
      case (c, i, v) => Alias(i, v, c)
    }

  def adtFreeForm[_: P]: P[RawAdt] = P(any ~ "=" ~/ any ~ sepAdtFreeForm.? ~ any ~ adt(sepAdtFreeForm))

  def adtEnclosed[_: P]: P[RawAdt] = P(NoCut(aggregates.enclosed(adt(sepAdt) ~ sepAdt.?)) | aggregates.enclosed(adt(sepAdtFreeForm)))

  def adtContract[_: P]: P[RawStructure] = aggregates.enclosed(Struct.struct).map(_.structure)

  def withAdtContract[_: P]: P[Option[RawStructure]] = P((inline ~ "with" ~ inline ~ adtContract ~ inline).?)

  def adtBlock[_: P]: P[Adt] = P(metaAgg.cstarting(kw.adt, withAdtContract ~ (adtEnclosed | adtFreeForm)))
    .map {
      case (c, i, (contract, v)) =>
        Adt(i, contract, v.alternatives, c)
    }

  def enumFreeForm[_: P]: P[RawEnum] = P(any ~ "=" ~/ any ~ sepEnumFreeForm.? ~ any ~ Enum.enum(sepEnumFreeForm))

  def enumEnclosed[_: P]: P[RawEnum] = P(NoCut(aggregates.enclosed(Enum.enum(sepEnum) ~ sepEnum.?)) | aggregates.enclosed(Enum.enum(sepEnumFreeForm)))


  def enumBlock[_: P]: P[Enumeration] = P(metaAgg.cstarting(kw.enum, enumEnclosed | enumFreeForm))
    .map {
      case (c, i, v) =>
        Enumeration(i, v, c)
    }


  def instanceBlock[_: P]: P[TLDInstance] = P(metaAgg.cstarting(kw.instance, "=" ~/ (inline ~ ids.typeReference)))
    .map {
      case (c, i, v) => TLDInstance(Instance(i, v, c))
    }

  def cloneBlock[_: P]: P[TLDNewtype] = P(metaAgg.cstarting(kw.newtype, "from" ~/ (inline ~ ids.typeNameRef ~ inline ~ aggregates.enclosed(Clone.struct).?)))
    .map {
      case (c, target, (src, struct)) =>
        NewType(target, src, struct, c)
    }
    .map(TLDNewtype)
}
