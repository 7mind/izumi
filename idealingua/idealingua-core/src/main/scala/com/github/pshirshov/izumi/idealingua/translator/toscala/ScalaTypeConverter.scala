package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException

import scala.meta._
import scala.reflect.{ClassTag, classTag}

case class Fields(unique: List[ScalaField], nonUnique: List[ScalaField]) {
  def all: List[ScalaField] = unique ++ nonUnique
}

class ScalaTypeConverter() {

  implicit class ExtendedFieldSeqOps(fields: Seq[ExtendedField]) {
    def toScala: Fields = {

      val conflicts = fields
        .groupBy(_.field.name)

      val (goodFields, conflictingFields) = conflicts.partition(_._2.lengthCompare(1) == 0)

      val (softConflicts, hardConflicts) = conflictingFields
        .map(kv => (kv._1, kv._2.groupBy(_.field)))
        .partition(_._2.size == 1)

      if (hardConflicts.nonEmpty) {
        throw new IDLException(s"Conflicting fields: $hardConflicts")
      }

      Fields(
        goodFields.flatMap(f => f._2.map(ef => toScala(ef.field))).toList
        , softConflicts.flatMap(_._2).keys.map(f => toScala(f)).toList
      )
    }

    private def toScala(field: Field): ScalaField = {
      ScalaField(Term.Name(field.name), ScalaTypeConverter.this.toScala(field.typeId).typeFull)
    }
  }

  implicit class ScalaTypeOps(st: ScalaType) {
    def sibling(name: TypeName): ScalaType = {
      toScala(JavaType(st.javaType.pkg, name))
    }

    def within(name: TypeName): ScalaType = {
      toScala(JavaType(st.javaType.pkg :+ st.javaType.name, name))
    }
  }

  def toScala(id: TypeId): ScalaType = {
    id match {
      case t: Primitive =>
        toScala(toPrimitive(t))

      case t: Generic =>
        toScala(toGeneric(t), t.args)

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
    toScala(javaType, List.empty)
  }

  private def toScala(javaType: JavaType, args: List[TypeId]): ScalaType = {
    ScalaType(
      toSelectTerm(javaType)
      , toSelect(javaType)
      , Term.Name(javaType.name)
      , Type.Name(javaType.name)
      , javaType
      , args.map(toScala(_).typeFull)
      , args.map(toScala(_).termFull)
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

  private def toGeneric(typeId: Generic): JavaType = {
    typeId match {
      case _: Generic.TSet =>
        JavaType(Seq.empty, "Set")
      case _: Generic.TMap =>
        JavaType(Seq.empty, "Map")
      case _: Generic.TList =>
        JavaType(Seq.empty, "List")
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

  private def selectTerm(id: JavaType): Option[Term.Ref] = {
    id.pkg.headOption.map {
      head =>
        id.pkg.tail.map(v => Term.Select(_: Term, Term.Name(v))).foldLeft(Term.Name(head): Term.Ref) {
          case (acc, v) =>
            v(acc)
        }

    }
  }
}



