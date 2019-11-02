package izumi.idealingua.translator.toscala.types

import izumi.idealingua.model.JavaType
import izumi.idealingua.model.common.DomainId

import scala.meta.{Init, Name, Term, Type}


final case class ScalaTypeImpl(
                          termAbsoluteBase: Term.Ref
                          , typeAbsoluteBase: Type.Ref
                          , termBase: Term.Ref
                          , typeBase: Type.Ref
                          , termName: Term.Name
                          , typeName: Type.Name

                          , fullJavaType: JavaType
                          , domainId: DomainId
                          , typeArgs: List[Type]
                          , termArgs: List[Term]
                        ) extends ScalaType {

  override def parameterize(names: List[Type]): ScalaType = copy(typeArgs = names)


  override def termAbsolute: Term = if (termArgs.isEmpty) {
    termAbsoluteBase
  } else {
    Term.Apply(termAbsoluteBase, termArgs)
  }

  override def typeAbsolute: Type = if (typeArgs.isEmpty) {
    typeAbsoluteBase
  } else {
    Type.Apply(typeAbsoluteBase, typeArgs)
  }

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
