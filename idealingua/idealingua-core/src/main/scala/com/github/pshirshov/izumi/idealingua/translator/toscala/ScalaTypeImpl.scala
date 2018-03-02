package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.JavaType

import scala.meta.{Init, Name, Term, Type}


case class ScalaTypeImpl(
                          termBase: Term.Ref
                          , typeBase: Type.Ref
                          , termName: Term.Name
                          , typeName: Type.Name
                          , fullJavaType: JavaType
                          , typeArgs: List[Type]
                          , termArgs: List[Term]
                        ) extends ScalaType {

  override def parameterize(names: List[Type]): ScalaType = copy(typeArgs = names)

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
