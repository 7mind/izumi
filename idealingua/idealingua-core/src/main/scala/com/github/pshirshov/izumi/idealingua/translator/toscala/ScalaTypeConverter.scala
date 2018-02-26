package com.github.pshirshov.izumi.idealingua.translator.toscala

import java.time.{LocalDate, LocalDateTime, LocalTime, ZonedDateTime}
import java.util.UUID

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il
import com.github.pshirshov.izumi.idealingua.model.il.{DomainId, JavaType}

import scala.meta._
import scala.reflect.{ClassTag, classTag}

case class Fields(unique: List[ScalaField], nonUnique: List[ScalaField]) {
  def all: List[ScalaField] = unique ++ nonUnique
}

class ScalaTypeConverter(domain: DomainId) {

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

  def toImport: Import = q"import ${toSelectTerm(JavaType(domain).withRoot)}._"

  def toScala(id: TypeId): ScalaType = {
    id match {
      case t: Generic =>
        toScala(toGeneric(t), t.args)

      case t: Primitive =>
        toScala(toPrimitive(t))

      case _ =>
        toScala(il.JavaType(id.pkg, id.name))
    }
  }

  def toMethodAst[T <: TypeId : ClassTag](typeId: T): Defn.Def = {
    val tpe = toSelect(JavaType.get[T].minimize(domain))
    q"def toTypeId: $tpe = { ${toAst(typeId)} }"
  }

  def toAst[T <: TypeId](typeId: T): Term.Apply = {
    typeId match {
      case i: InterfaceId =>
        toIdConstructor(i)
      case i: DTOId =>
        toIdConstructor(i)
      case i: AliasId =>
        toIdConstructor(i)
      case i: EnumId =>
        toIdConstructor(i)
      case i: IdentifierId =>
        toIdConstructor(i)
      case i: ServiceId =>
        toIdConstructor(i)
      case i: UserType =>
        toIdConstructor(i)
      case i: AdtId =>
        toIdConstructor(i)
      case i: EphemeralId =>
        q"${toSelectTerm(JavaType.get[EphemeralId].minimize(domain))}(${toAst(i.parent)}, ${Lit.String(i.name)})"
    }
  }

  private def toIdConstructor[T <: TypeId : ClassTag](t: T): Term.Apply = {
    q"${toSelectTerm(JavaType.get[T].minimize(domain))}(Seq(..${t.pkg.map(Lit.String.apply).toList}), ${Lit.String(t.name)})"
  }

  def toScala[T: ClassTag]: ScalaType = {
    val idtClass = classTag[T].runtimeClass
    val javaType = JavaType(UserType(idtClass.getPackage.getName.split('.'), idtClass.getSimpleName))
    toScala(javaType)
  }

  def toScala(javaType: JavaType): ScalaType = {
    toScala(javaType, List.empty)
  }

  private def toScala(javaType: JavaType, args: List[TypeId]): ScalaType = {
    val minimized = javaType.minimize(domain)
    ScalaType(
      toSelectTerm(minimized)
      , toSelect(minimized)
      , Term.Name(minimized.name)
      , Type.Name(minimized.name)
      , minimized
      , args.map(toScala(_).typeFull)
      , args.map(toScala(_).termFull)
    )
  }


  private def toPrimitive(id: Primitive): JavaType = id match {
    case Primitive.TString =>
      il.JavaType(Seq.empty, "String")
    case Primitive.TInt8 =>
      il.JavaType(Seq.empty, "Byte")
    case Primitive.TInt16 =>
      il.JavaType(Seq.empty, "Short")
    case Primitive.TInt32 =>
      il.JavaType(Seq.empty, "Int")
    case Primitive.TInt64 =>
      il.JavaType(Seq.empty, "Long")
    case Primitive.TFloat =>
      il.JavaType(Seq.empty, "Float")
    case Primitive.TDouble =>
      il.JavaType(Seq.empty, "Double")
    case Primitive.TUUID =>
      il.JavaType.get[UUID]
    case Primitive.TTsTz =>
      il.JavaType.get[ZonedDateTime]
    case Primitive.TTs =>
      il.JavaType.get[LocalDateTime]
    case Primitive.TTime =>
      il.JavaType.get[LocalTime]
    case Primitive.TDate =>
      il.JavaType.get[LocalDate]
  }

  private def toGeneric(typeId: Generic): JavaType = {
    typeId match {
      case _: Generic.TSet =>
        il.JavaType.get[Set[_]]
      case _: Generic.TMap =>
        il.JavaType.get[Map[_, _]]
      case _: Generic.TList =>
        il.JavaType.get[List[_]]
      case _: Generic.TOption =>
        il.JavaType.get[Option[_]]
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



