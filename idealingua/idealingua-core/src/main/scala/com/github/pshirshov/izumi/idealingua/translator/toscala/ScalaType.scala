package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.JavaType

import scala.meta.{Init, Term, Type}

trait ScalaType {
  def parameterize(names: List[Type]): ScalaType

  def termBase: Term.Ref
  def termName: Term.Name
  def termArgs: Seq[Term]
  def termFull: Term

  def typeBase: Type.Ref
  def typeArgs: Seq[Type]
  def typeName: Type.Name
  def typeFull: Type
  def fullJavaType: JavaType
  def init(constructorArgs: Term*): Init
}
