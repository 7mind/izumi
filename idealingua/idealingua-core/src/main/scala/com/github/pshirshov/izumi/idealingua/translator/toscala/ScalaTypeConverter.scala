package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.finaldef.Typespace

import scala.meta._
import scala.reflect.{ClassTag, classTag}

class ScalaTypeConverter(typespace: Typespace) {
  implicit class ExtendedFieldSeqOps(fields: Seq[ExtendedField]) {
    def toScala: List[ScalaField] = {
      fields.map(_.field).toScala
    }
  }

  implicit class FieldSeqOps(fields: Seq[Field]) {
    def toScala: List[ScalaField] = {
      val conflictingFields = fields.groupBy(_.name).filter(_._2.lengthCompare(1) > 0)
      if (conflictingFields.nonEmpty) {
        throw new IDLException(s"Conflicting fields: $conflictingFields")
      }

      fields.map(_.toScala).toList
    }
  }


  implicit class FieldOps(field: Field) {
    def toScala: ScalaField = {
      ScalaField(Term.Name(field.name), toScalaType(field.typeId))
    }
  }

  implicit class ExtendedFieldOps(field: ExtendedField) {
    protected def toScala: ScalaField = field.field.toScala
  }


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

  def toSelectTerm(id: JavaType): Term.Ref = {
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

  def toSelect(id: JavaType): Type.Ref = {
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

  // TODO: this thing is not safe and does not support packages
  def toScala(typeName: TypeName): ScalaType = {
    ScalaType(Term.Name(typeName), Type.Name(typeName), Term.Name(typeName), Type.Name(typeName))
  }

  def toScala[T: ClassTag]: ScalaType = {
    val idtClass = classTag[T].runtimeClass
    val javaType = UserType(idtClass.getPackage.getName.split('.'), idtClass.getSimpleName).toJava

    ScalaType(toSelectTerm(javaType), toSelect(javaType), Term.Name(javaType.name), Type.Name(javaType.name))
  }
}

case class ScalaType(term: Term.Ref, tpe: Type.Ref, termName: Term.Name, typeName: Type.Name) {
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
