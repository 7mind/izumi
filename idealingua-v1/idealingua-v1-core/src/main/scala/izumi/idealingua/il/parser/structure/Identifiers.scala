package izumi.idealingua.il.parser.structure

import izumi.idealingua.model.common.{AbstractIndefiniteId, DomainId}
import fastparse.CharPredicates.{isDigit, isLetter}
import fastparse._
import NoWhitespace._
import izumi.idealingua.model.il.ast.raw.defns.InterpContext
import izumi.idealingua.model.il.ast.raw.typeid.ParsedId

trait Identifiers extends Separators {
  def symbol[_: P]: P[String] = P((CharPred(c => isLetter(c)) ~ CharPred(c => isLetter(c) | isDigit(c) | c == '_').rep).!)

  def idPkg[_: P]: P[Seq[String]] = P(symbol.rep(sep = "."))

  def domainId[_: P]: P[DomainId] = P(idPkg)
    .map(v => DomainId(v.init, v.last))

  def idFull[_: P]: P[ParsedId] = P(idPkg ~ "#" ~ symbol).map(v => ParsedId(v._1, v._2))

  def idShort[_: P]: P[ParsedId] = P(symbol).map(v => ParsedId(v))

  def identifier[_: P]: P[ParsedId] = P(idFull | idShort)

  def idGeneric[_: P]: P[AbstractIndefiniteId] = P(inline ~ identifier ~ inline ~ generic.rep(min = 0, max = 1) ~ inline)
    .map(tp => tp._1.toGeneric(tp._2))

  def generic[_: P]: P[Seq[AbstractIndefiniteId]] = P("[" ~ inline ~ idGeneric.rep(sep = ",") ~ inline ~ "]")

  def staticPart[_: P]: P[String] = P(CharsWhile(c => c != '"' && c != '$').!)

  def expr[_: P]: P[ParsedId] = P("${" ~ idShort ~ "}")

  def justString[_: P]: P[InterpContext] = P("t\"" ~ staticPart ~ "\"")
    .map(s => InterpContext(Vector(s), Vector.empty))

  def interpString[_: P]: P[InterpContext] = P("t\"" ~ (staticPart ~ expr).rep ~ staticPart.? ~ "\"").map {
    case (parts, tail) =>
      val strs = parts.map(_._1) :+ tail.getOrElse("")
      val exprs = parts.map(_._2)
      InterpContext(strs, exprs.map(_.toIndefinite))
  }

  def typeInterp[_: P]: P[InterpContext] = P(interpString | justString)

}

