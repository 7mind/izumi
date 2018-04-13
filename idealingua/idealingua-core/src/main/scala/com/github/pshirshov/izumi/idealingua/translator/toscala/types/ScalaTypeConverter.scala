package com.github.pshirshov.izumi.idealingua.translator.toscala.types

import java.time.{LocalDate, LocalDateTime, LocalTime, ZonedDateTime}
import java.util.UUID

import com.github.pshirshov.izumi.idealingua.model
import com.github.pshirshov.izumi.idealingua.model.JavaType
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{DomainId, Tuple}
import com.github.pshirshov.izumi.idealingua.model.typespace.structures.{PlainStruct, Struct}

import scala.language.higherKinds
import scala.meta._
import scala.reflect.{ClassTag, classTag}


class ScalaTypeConverter(domain: DomainId) {
  protected def toScalaField(field: ExtendedField): ScalaField = {
    ScalaField(
      Term.Name(field.field.name)
      , ScalaTypeConverter.this.toScala(field.field.typeId).typeFull
      , field
    )
  }

  implicit class StructOps(fields: PlainStruct) {
    def toScala: PlainScalaStruct = {
      PlainScalaStruct(fields.all.map(toScalaField))
    }
  }

  implicit class ConflictsOps(fields: Struct) {
    def toScala: ScalaStruct = {
      val good = fields.unambigious.map(toScalaField)
      val soft = fields.ambigious.map(toScalaField)
      new ScalaStruct(fields, good, soft)
    }
  }


  implicit class ScalaTypeOps(st: ScalaType) {
    def sibling(name: TypeName): ScalaType = {
      toScala(JavaType(st.fullJavaType.pkg, name))
    }

    def within(name: TypeName): ScalaType = {
      toScala(JavaType(st.fullJavaType.pkg :+ st.fullJavaType.name, name))
    }
  }

  def toImport: Import = q"import ${toSelectTerm(JavaType(domain).withRoot)}._"

  def toScala(id: TypeId): ScalaType = {
    id match {
      case t: Generic =>
        toScala(toGeneric(t), t.args)

      case t: Primitive =>
        toScala(toPrimitive(t))

      case _ =>
        toScala(JavaType(id.pkg, id.name))
    }
  }


  def toScala[T: ClassTag]: ScalaType = {
    toScala(classTag[T].runtimeClass)
  }

  def toScala1[T[_]](implicit ev: ClassTag[T[_]]): ScalaType = {
    toScala(ev.runtimeClass)
  }

  def toScala(clazz: Class[_]): ScalaType = {
    val javaType = JavaType(IndefiniteId(clazz.getPackage.getName.split('.'), clazz.getSimpleName))
    toScala(javaType)
  }

  def toScala(id: IndefiniteId): ScalaType = {
    toScala(JavaType(id.pkg, id.name))
  }

  def toScala(javaType: JavaType): ScalaType = {
    toScala(javaType, List.empty)
  }

  private def toScala(javaType: JavaType, args: List[TypeId]): ScalaType = {
    val withRoot = javaType.withRoot
    val minimized = javaType.minimize(domain)
    ScalaTypeImpl(
      toSelectTerm(withRoot)
      , toSelect(withRoot)
      , toSelectTerm(minimized)
      , toSelect(minimized)
      , Term.Name(javaType.name)
      , Type.Name(javaType.name)
      , javaType
      , domain
      , args.map(toScala(_).typeFull)
      , args.map(toScala(_).termFull)
    )
  }


  private def toPrimitive(id: Primitive): JavaType = id match {
    case Primitive.TBool =>
      model.JavaType(Seq.empty, "Boolean")
    case Primitive.TString =>
      model.JavaType(Seq.empty, "String")
    case Primitive.TInt8 =>
      model.JavaType(Seq.empty, "Byte")
    case Primitive.TInt16 =>
      model.JavaType(Seq.empty, "Short")
    case Primitive.TInt32 =>
      model.JavaType(Seq.empty, "Int")
    case Primitive.TInt64 =>
      model.JavaType(Seq.empty, "Long")
    case Primitive.TFloat =>
      model.JavaType(Seq.empty, "Float")
    case Primitive.TDouble =>
      model.JavaType(Seq.empty, "Double")
    case Primitive.TUUID =>
      JavaType.get[UUID]
    case Primitive.TTsTz =>
      JavaType.get[ZonedDateTime]
    case Primitive.TTs =>
      JavaType.get[LocalDateTime]
    case Primitive.TTime =>
      JavaType.get[LocalTime]
    case Primitive.TDate =>
      JavaType.get[LocalDate]
  }

  private def toGeneric(typeId: Generic): JavaType = {
    typeId match {
      case _: Generic.TSet =>
        JavaType.get[Set[_]]
      case _: Generic.TMap =>
        JavaType.get[Map[_, _]]
      case _: Generic.TList =>
        JavaType.get[List[_]]
      case _: Generic.TOption =>
        JavaType.get[Option[_]]
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



