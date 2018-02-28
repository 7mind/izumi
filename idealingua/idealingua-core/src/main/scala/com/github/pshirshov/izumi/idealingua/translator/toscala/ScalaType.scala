package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.il.JavaType

import scala.meta.{Init, Name, Term, Type}

trait IScalaType {
  def termBase: Term.Ref
  def termName: Term.Name
  def termArgs: Seq[Term]
  def termFull: Term

  def typeBase: Type.Ref
  def typeArgs: Seq[Type]
  def typeName: Type.Name
  def typeFull: Type

  def javaType: JavaType
}

case class ScalaType(
                      termBase: Term.Ref
                      , typeBase: Type.Ref
                      , termName: Term.Name
                      , typeName: Type.Name
                      , javaType: JavaType
                      , fullJavaType: JavaType
                      , typeArgs: List[Type]
                      , termArgs: List[Term]
                    ) extends IScalaType {
  def termFull: Term = if (termArgs.isEmpty) {
    termBase
  } else {
    Term.Apply(termBase, termArgs)
  }

  def typeFull: Type = if (typeArgs.isEmpty) {
    typeBase
  } else {
    Type.Apply(typeBase, typeArgs)
  }

  def init(constructorArgs: Term*): Init = {
    val cargs = if (constructorArgs.isEmpty) {
      List.empty
    } else {
      List(constructorArgs.toList)
    }

    Init(typeFull, Name.Anonymous(), cargs)
  }

}
