package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.il.parser.model.{AlgebraicType, ParsedStruct, StructOp}
import com.github.pshirshov.izumi.idealingua.il.parser.structure.{Separators, ids}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL.ImportedId
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

}

object DefStructure extends DefStructure {
}
