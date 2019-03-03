package com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid

import com.github.pshirshov.izumi.idealingua.model.common._

final case class ParsedId(pkg: Seq[String], name: String)

object ParsedId {
  def apply(name: String): ParsedId = new ParsedId(Seq.empty, name)
}


sealed trait RawRef {
  def name: TypeName
  def pkg: Package
}
final case class RawNongenericRef(pkg: Package, name: TypeName) extends RawRef
final case class RawGenericRef(pkg: Package, name: TypeName, args: List[RawRef]) extends RawRef

sealed trait TemplateDecl {
  def name: TypeName
}
final case class RawTemplateNoArg(name: TypeName) extends TemplateDecl
final case class RawTemplateWithArg(name: TypeName, args: List[RawTemplateNoArg]) extends TemplateDecl


final case class RawDeclaredTypeName(name: TypeName)

