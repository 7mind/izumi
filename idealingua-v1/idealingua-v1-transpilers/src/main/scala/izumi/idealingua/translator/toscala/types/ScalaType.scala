package izumi.idealingua.translator.toscala.types

import izumi.idealingua.model.JavaType

import scala.meta.{Init, Term, Type}

trait ScalaType {
  def parameterize(names: List[Type]): ScalaType
  def parameterize(names: String*): ScalaType = {
    parameterize(names.map(Type.Name.apply).toList)
  }

  def termBase: Term.Ref
  def termName: Term.Name
  def termArgs: Seq[Term]
  def termFull: Term
  def termAbsolute: Term

  def typeBase: Type.Ref
  def typeArgs: Seq[Type]
  def typeName: Type.Name
  def typeFull: Type

  def typeAbsolute: Type

  def fullJavaType: JavaType
  def init(constructorArgs: Term*): Init
}
