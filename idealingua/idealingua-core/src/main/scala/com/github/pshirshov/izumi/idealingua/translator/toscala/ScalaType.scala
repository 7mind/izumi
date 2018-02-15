package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.common.JavaType

import scala.meta.{Init, Name, Term, Type}

case class ScalaType(
                      term: Term.Ref
                      , tpe: Type
                      , termName: Term.Name
                      , typeName: Type.Name
                      , javaType: JavaType
                    ) {


  def init(): Init = init(List.empty)

  def init(typeArgs: List[Type], constructorArgs: Term*): Init = {
    val cargs = if (constructorArgs.isEmpty) {
      List.empty
    } else {
      List(constructorArgs.toList)
    }

    if (typeArgs.isEmpty) {
      Init(tpe, Name.Anonymous(), cargs)
    } else {
      Init(Type.Apply(tpe, typeArgs), Name.Anonymous(), cargs)
    }
  }

}
