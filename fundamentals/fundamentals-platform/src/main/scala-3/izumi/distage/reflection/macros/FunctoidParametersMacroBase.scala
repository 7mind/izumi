package izumi.distage.reflection.macros

import izumi.distage.model.reflection.LinkedParameter

import scala.quoted.{Expr, Quotes}

trait FunctoidParametersMacroBase[Q <: Quotes] {
  val qctx: Q

  import qctx.reflect.*
  def makeParam(name: String, tpe: Either[TypeTree, TypeRepr], annotSym: Option[Symbol]): Expr[LinkedParameter]
  def makeParam(name: String, tpe: Either[TypeTree, TypeRepr], annotSym: Option[Symbol], annotTpe: Either[TypeTree, TypeRepr]): Expr[LinkedParameter]
}
