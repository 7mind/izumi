package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.finaldef.Typespace

import scala.meta._
import scala.reflect.{ClassTag, classTag}

class ScalaTypeConverter(typespace: Typespace) {
  def toScalaType(typeId: Primitive): Type = {
    typeId match {
      case Primitive.TString =>
        t"String"
      case Primitive.TInt32 =>
        t"Int"
      case Primitive.TInt64 =>
        t"Long"
    }
  }

  def toScalaType(typeId: Generic): Type = {
    typeId match {
      case t: Generic.TSet =>
        t"Set[${toScalaType(t.valueType)}]"
      case t: Generic.TMap =>
        t"Map[${toScalaType(t.keyType)}, ${toScalaType(t.valueType)}]"
      case t: Generic.TList =>
        t"List[${toScalaType(t.valueType)}]"
    }
  }

  def toScalaType(typeId: TypeId): Type = {
    typeId match {
      case t: Primitive =>
        toScalaType(t)

      case t: Generic =>
        toScalaType(t)

      case _ =>
        val typedef = typespace(typeId)
        toSelect(typedef.id.toJava)
    }
  }

  def toSelectTerm(id: JavaType): Term = {
    val maybeSelect: Option[Term.Ref] = id.pkg.headOption.map {
      head =>
        id.pkg.tail.map(v => Term.Select(_: Term, Term.Name(v))).foldLeft(Term.Name(head): Term.Ref) {
          case (acc, v) =>
            v(acc)
        }

    }
    maybeSelect match {
      case Some(v) =>
        Term.Select(v, Term.Name(id.name))

      case None =>
        Term.Name(id.name)
    }
  }

  def toSelect(id: JavaType): Type = {
    val maybeSelect: Option[Term.Ref] = id.pkg.headOption.map {
      head =>
        id.pkg.tail.map(v => Term.Select(_: Term, Term.Name(v))).foldLeft(Term.Name(head): Term.Ref) {
          case (acc, v) =>
            v(acc)
        }

    }
    maybeSelect match {
      case Some(v) =>
        Type.Select(v, Type.Name(id.name))

      case None =>
        Type.Name(id.name)
    }
  }

  def initFor[T: ClassTag]: Init = {
    init[T](List.empty)
  }

  def init[T: ClassTag](typeArgs: List[Type], constructorArgs: Term*): Init = {
    val idtClass = classTag[T].runtimeClass
    val select = toSelect(UserType(idtClass.getPackage.getName.split('.'), idtClass.getSimpleName).toJava)

    val cargs = if (constructorArgs.isEmpty) {
      List.empty
    } else {
      List(constructorArgs.toList)
    }

    if (typeArgs.isEmpty) {
      Init(select, Name.Anonymous(), cargs)
    } else {
      Init(Type.Apply(select, typeArgs), Name.Anonymous(), cargs)
    }
  }

}
