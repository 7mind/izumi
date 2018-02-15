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

  implicit class ExtendedFieldOps(field: ExtendedField) {
    protected def toScala: ScalaField = field.field.toScala
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
      ScalaField(Term.Name(field.name), ScalaTypeConverter.this.toScala(field.typeId).tpe)
    }
  }

  implicit class ScalaTypeOps(st: ScalaType) {
    def within(name: TypeName): ScalaType = {
      toScala(JavaType(st.javaType.pkg :+ st.javaType.name, name))
    }
  }

  def toScala(id: TypeId): ScalaType = {
    id match {
      case t: Primitive =>
        toScala(toPrimitive(t))

      case t: Generic =>
        // TODO: looks like shit
        ScalaType(null, toGeneric(t), null, null, null)

      case _ =>
        toScala(JavaType(id.pkg, id.name))
    }
  }

  def toScala[T: ClassTag]: ScalaType = {
    val idtClass = classTag[T].runtimeClass
    val javaType = UserType(idtClass.getPackage.getName.split('.'), idtClass.getSimpleName).toJava
    toScala(javaType)
  }

  def toScala(javaType: JavaType): ScalaType = {
    ScalaType(
      toSelectTerm(javaType)
      , toSelect(javaType)
      , Term.Name(javaType.name)
      , Type.Name(javaType.name)
      , javaType
    )
  }

  private def toPrimitive(id: Primitive): JavaType = {
    id match {
      case Primitive.TString =>
        JavaType(Seq.empty, "String")
      case Primitive.TInt32 =>
        JavaType(Seq.empty, "Int")
      case Primitive.TInt64 =>
        JavaType(Seq.empty, "Long")
    }
  }

  private def toGeneric(typeId: Generic): Type.Apply = {
    typeId match {
      case t: Generic.TSet =>
        t"Set[${toScala(t.valueType).tpe}]"
      case t: Generic.TMap =>
        t"Map[${toScala(t.keyType).tpe}, ${toScala(t.valueType).tpe}]"
      case t: Generic.TList =>
        t"List[${toScala(t.valueType).tpe}]"
    }
  }

  private def toSelectTerm(id: JavaType): Term.Ref = {
    selectTerm(id) match {
      case Some(v) =>
        Term.Select(v, Term.Name(id.name))

      case None =>
        Term.Name(id.name)
    }
  }

  private def toSelect(id: JavaType): Type.Ref = {
    selectTerm(id) match {
      case Some(v) =>
        Type.Select(v, Type.Name(id.name))

      case None =>
        Type.Name(id.name)
    }
  }

  private def selectTerm(id: JavaType) = {
    val maybeSelect: Option[Term.Ref] = id.pkg.headOption.map {
      head =>
        id.pkg.tail.map(v => Term.Select(_: Term, Term.Name(v))).foldLeft(Term.Name(head): Term.Ref) {
          case (acc, v) =>
            v(acc)
        }

    }
    maybeSelect
  }
}



