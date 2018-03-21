package com.github.pshirshov.izumi.idealingua.translator.toscala

import java.time.{LocalDate, LocalDateTime, LocalTime, ZonedDateTime}
import java.util.UUID

import com.github.pshirshov.izumi.idealingua.model
import com.github.pshirshov.izumi.idealingua.model.JavaType
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il._

import scala.meta._
import scala.reflect.{ClassTag, classTag}


class ScalaTypeConverter(domain: DomainId) {

  implicit class ConflictsOps(fields: Fields) {
    def toScala: ScalaFields = {
      val good = fields.conflicts.goodFields
        .flatMap(f => f._2.map(ef => toScala(ef))).toList

      val soft = fields.conflicts.softConflicts
        .flatMap(_._2).map(kv => toScala(kv._2)).toList

      ScalaFields(good, soft, fields)
    }

    private def toScala(fields: Seq[ExtendedField]): ScalaField = {
      val primary = fields.head
      toScala(primary).copy(conflicts = fields.toSet)
    }

    private def toScala(field: ExtendedField): ScalaField = {
      ScalaField(
        Term.Name(field.field.name)
        , ScalaTypeConverter.this.toScala(field.field.typeId).typeFull
        , field
        , Set.empty
      )
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
      case i: Indefinite =>
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

  def toIdConstructor(t: DomainId): Term.Apply = {
    q"${toSelectTerm(JavaType.get[DomainId])}(Seq(..${t.pkg.map(Lit.String.apply).toList}), ${Lit.String(t.id)})"
  }


  def toScala[T: ClassTag]: ScalaType = {
    val idtClass = classTag[T].runtimeClass
    val javaType = JavaType(Indefinite(idtClass.getPackage.getName.split('.'), idtClass.getSimpleName))
    toScala(javaType)
  }

  def toScala(id: Indefinite): ScalaType = {
    toScala(JavaType(id.pkg, id.name))
  }

  def toScala(javaType: JavaType): ScalaType = {
    toScala(javaType, List.empty)
  }

  def domainCompanionId(domainDefinition: DomainDefinition): Indefinite = {
    Indefinite(Seq("izumi", "idealingua", "domains"), domainDefinition.id.id.capitalize)
  }


  private def toScala(javaType: JavaType, args: List[TypeId]): ScalaType = {
    val minimized = javaType.minimize(domain)
    ScalaTypeImpl(
      toSelectTerm(minimized)
      , toSelect(minimized)
      , Term.Name(javaType.name)
      , Type.Name(javaType.name)
      , javaType
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



