package com.github.pshirshov.izumi.idealingua.il.parser.structure

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.InterpContext
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid._
import fastparse.CharPredicates.{isDigit, isLetter}
import fastparse.NoWhitespace._
import fastparse._

trait Identifiers extends Separators {
  // entity names
  def methodName[_: P]: P[String] = symbol

  def adtMemberName[_: P]: P[String] = symbol

  def fieldName[_: P]: P[String] = symbol | P("_" | "").map(_ => "")

  def importedName[_: P]: P[String] = symbol

  def enumMemberName[_: P]: P[String] = symbol

  def annoName[_: P]: P[String] = symbol

  def constName[_: P]: P[String] = symbol

  def removedParentName[_: P]: P[String] = symbol

  private def symbol[_: P]: P[String] = P((CharPred(c => isLetter(c)) ~ CharPred(c => isLetter(c) | isDigit(c) | c == '_').rep).!)

  // packages
  def domainId[_: P]: P[DomainId] = P(idPkg)
    .map(v => DomainId(v.init, v.last))

  private def idPkg[_: P]: P[Seq[String]] = P(symbol.rep(sep = "."))

  // type definitions
  def parentStruct[_: P]: P[RawRef] = typeReference //typename.map(id => RawNongenericRef(id.pkg, id.name))

  def parentEnum[_: P]: P[RawNongenericRef] = typename.map(id => RawNongenericRef(id.pkg, id.name))

  def typeNameRef[_: P]: P[RawTypeNameRef] = typename.map(id => RawTypeNameRef(id.pkg, id.name))

  def typeReferenceLocal[_: P]: P[TemplateDecl] = P(inline ~ typenameShort ~ inline ~ typeArgumentsShort.? ~ inline).map {
    case (id, None) =>
      RawTemplateNoArg(id.name)
    case (id, Some(args)) =>
      RawTemplateWithArg(id.name, args.toList)
  }

  def typeReference[_: P]: P[RawRef] = P(inline ~ typename ~ inline ~ typeArguments.? ~ (inline ~ "%" ~ symbol).? ~ inline)
    .map {
      case (tn, args, adhocName) =>
        args match {
          case Some(value) =>
            RawGenericRef(tn.pkg, tn.name, value.toList, adhocName)

          case None =>
            RawNongenericRef(tn.pkg, tn.name)
        }
    }

  def declaredTypeName[_: P]: P[RawDeclaredTypeName] = typenameShort.map(f => RawDeclaredTypeName(f.name))

  private def typename[_: P]: P[ParsedId] = P(typenameFull | typenameShort)

  private def typenameShort[_: P]: P[ParsedId] = P(symbol).map(v => ParsedId(v))

  private def typenameFull[_: P]: P[ParsedId] = P(idPkg ~ "#" ~ symbol).map(v => ParsedId(v._1, v._2))

  private def typeArguments[_: P]: P[Seq[RawRef]] = P("[" ~ inline ~ typeReference.rep(sep = ",") ~ inline ~ "]")

  def typeArgumentsShort[_: P]: P[Seq[RawTemplateNoArg]] = P("[" ~ (inline ~ symbol ~ inline).rep(sep = ",") ~ "]").map {
    args =>
      args.map(name => RawTemplateNoArg(name))
  }

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

