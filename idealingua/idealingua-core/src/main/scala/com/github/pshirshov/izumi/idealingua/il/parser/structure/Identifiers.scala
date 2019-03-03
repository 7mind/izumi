package com.github.pshirshov.izumi.idealingua.il.parser.structure

import com.github.pshirshov.izumi.idealingua.model.common.{AbstractIndefiniteId, DomainId}
import fastparse.CharPredicates.{isDigit, isLetter}
import fastparse._
import NoWhitespace._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.InterpContext
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.ParsedId

trait Identifiers extends Separators {
  // entity names
  def methodName[_: P]: P[String] = symbol
  def adtMemberName[_: P]: P[String] = symbol
  def fieldName[_: P]: P[String] = symbol | P("_" | "").map(_ => "")
  def importedName[_: P]: P[String] = symbol
  def enumMemberName[_: P]: P[String] = symbol
  def removedParentName[_: P]: P[String] = symbol
  def annoName[_: P]: P[String] = symbol
  def constName[_: P]: P[String] = symbol

  private def symbol[_: P]: P[String] = P((CharPred(c => isLetter(c)) ~ CharPred(c => isLetter(c) | isDigit(c) | c == '_').rep).!)

  // packages
  def domainId[_: P]: P[DomainId] = P(idPkg)
    .map(v => DomainId(v.init, v.last))

  private def idPkg[_: P]: P[Seq[String]] = P(symbol.rep(sep = "."))

  // type definitions
  def typename[_: P]: P[ParsedId] = P(typenameFull | typenameShort)

  def typeReference[_: P]: P[AbstractIndefiniteId] = P(inline ~ typename ~ inline ~ typeArguments.rep(min = 0, max = 1) ~ inline)
    .map(tp => tp._1.toGeneric(tp._2))

  def declaredTypeName[_: P]: P[ParsedId] = typenameShort

  private def typenameShort[_: P]: P[ParsedId] = P(symbol).map(v => ParsedId(v))

  private def typenameFull[_: P]: P[ParsedId] = P(idPkg ~ "#" ~ symbol).map(v => ParsedId(v._1, v._2))

  private def typeArguments[_: P]: P[Seq[AbstractIndefiniteId]] = P("[" ~ inline ~ typeReference.rep(sep = ",") ~ inline ~ "]")

  // interpolators
  def interpString[_: P]: P[InterpContext] = P("t\"" ~ (staticPart ~ expr).rep ~ staticPart.? ~ "\"").map {
    case (parts, tail) =>
      val strs = parts.map(_._1) :+ tail.getOrElse("")
      val exprs = parts.map(_._2)
      InterpContext(strs, exprs)
  }

  def typeInterp[_: P]: P[InterpContext] = P(interpString | justString)

  private def staticPart[_: P]: P[String] = P(CharsWhile(c => c != '"' && c != '$').!)

  private def expr[_: P]: P[String] = P("${" ~ typenameShort ~ "}").map(_.name)

  private def justString[_: P]: P[InterpContext] = P("t\"" ~ staticPart ~ "\"")
    .map(s => InterpContext(Vector(s), Vector.empty))

}

