package com.github.pshirshov.izumi.idealingua.il.parser.structure

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.RawDeclaredTypeName
import fastparse.NoWhitespace._
import fastparse._

trait Aggregates
  extends Separators
    with Identifiers {


  def enclosed[T](defparser: => P[T])(implicit v: P[_]): P[T] = {
    P(inCurlyBraces(defparser) | inBraces(defparser))
  }

  def inCurlyBraces[T](defparser: => P[T])(implicit v: P[_]): P[T] = {
    P("{" ~ any ~ defparser ~ any ~ "}")
  }

  def inBraces[T](defparser: => P[T])(implicit v: P[_]): P[T] = {
    P("(" ~ any ~ defparser ~ any ~ ")")
  }


  def inBrackets[T](defparser: => P[T])(implicit v: P[_]): P[T] = {
    P("[" ~ any ~ defparser ~ any ~ "]")
  }


  def typeDecl[T](keyword: => P[Unit], defparser: => P[T])(implicit v: P[_]): P[(RawDeclaredTypeName, T)] = {
    kw(keyword, declaredTypeName ~ inline ~ defparser)
  }

  def block[T](keyword: => P[Unit], defparser: => P[T])(implicit v: P[_]): P[(RawDeclaredTypeName, T)] = {
    typeDecl(keyword, enclosed(defparser))
  }


}


