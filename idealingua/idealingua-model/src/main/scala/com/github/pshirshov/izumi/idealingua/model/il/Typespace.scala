package com.github.pshirshov.izumi.idealingua.model.il

import com.github.pshirshov.izumi.idealingua.model.common
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{DTOId, EphemeralId, InterfaceId}
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.DefMethod.RPCMethod
import com.github.pshirshov.izumi.idealingua.model.il.FinalDefinition._


class Typespace(val domain: DomainDefinition) {
  protected val typespace: Map[UserType, FinalDefinition] = verified(domain.types)

  protected val serviceEphemerals: Map[EphemeralId, Composite] = (for {
    service <- domain.services
    method <- service.methods
  } yield {
    method match {
      case m: RPCMethod =>
        val inId = EphemeralId(service.id, s"In${m.name.capitalize}")
        val outId = EphemeralId(service.id, s"Out${m.name.capitalize}")

        Seq(
          inId -> m.signature.input
          , outId -> m.signature.output
        )
    }
  }).flatten.toMap

  def all: List[TypeId] = List(
    typespace.keys
    , serviceEphemerals.keys
    , domain.services.map(_.id)
    , domain.types.collect({ case t: Enumeration => t }).flatMap(e => e.members.map(m => EphemeralId(e.id, m)))
  ).flatten

  def verify(): Unit = {
    import Typespace._
    val typeDependencies = domain.types.flatMap {
      case _: Enumeration =>
        Seq.empty
      case d: Interface =>
        d.interfaces.map(i => Dependency.Interface(d.id, i)) ++ d.fields.map(f => Dependency.Field(d.id, f))
      case d: DTO =>
        d.interfaces.map(i => Dependency.Interface(d.id, i))
      case d: Identifier =>
        d.fields.map(f => Dependency.Field(d.id, f))
      case d: Alias =>
        Seq(Dependency.Alias(d.id, d.target))
    }

    val serviceDependencies = for {
      service <- domain.services
      method <- service.methods
    } yield {
      method match {
        case m: RPCMethod =>
          (m.signature.input ++ m.signature.output).map(i => Dependency.Parameter(service.id, i))
      }
    }

    val allDependencies = typeDependencies ++ serviceDependencies.flatten

    val missingTypes = allDependencies
      .filterNot(_.typeId.isInstanceOf[Builtin])
      .filterNot(d => typespace.contains(toKey(d.typeId)))

    if (missingTypes.nonEmpty) {
      throw new IDLException(s"Incomplete typespace: $missingTypes")
    }

    //    val martians = all.filterNot(domain.id.contains)
    //    if (martians.nonEmpty) {
    //      throw new IDLException(s"Martian types: $martians")
    //    }
  }

  def toKey(typeId: TypeId): UserType = {
    if (typeId.pkg.isEmpty) {
      UserType(domain.id.toPackage, typeId.name)
    } else {
      UserType(typeId)
    }
  }

  protected def verified(types: Seq[FinalDefinition]): Map[UserType, FinalDefinition] = {
    val conflictingTypes = types.groupBy(_.id.name).filter(_._2.lengthCompare(1) > 0)
    if (conflictingTypes.nonEmpty) {
      throw new IDLException(s"Conflicting types in: $conflictingTypes")
    }
    types
      .groupBy(k => toKey(k.id))
      .mapValues(_.head)
  }

  def apply(id: TypeId): FinalDefinition = typespace.apply(toKey(id))

  def apply(id: InterfaceId): Interface = typespace.apply(toKey(id)).asInstanceOf[Interface]

  def apply(id: EphemeralId): Composite = serviceEphemerals.apply(id)

  def apply(id: DTOId): DTO = typespace.apply(toKey(id)).asInstanceOf[DTO]

  def implements(id: TypeId): List[InterfaceId] = {
    id match {
      case i: InterfaceId =>
        List(i) ++ apply(i).interfaces.toList.flatMap(implements)

      case i: DTOId =>
        apply(i).interfaces.toList.flatMap(implements)

      case i: EphemeralId =>
        serviceEphemerals(i).toList.flatMap(implements)

      case _ =>
        List.empty
    }
  }

  def implementingDtos(id: InterfaceId): List[DTOId] = {
    typespace.collect {
      case (tid, _: DTO) if implements(tid).contains(id) =>
        tid.toDTO
    }.toList
  }

  def implementingEphemerals(id: InterfaceId): List[EphemeralId] = {
    serviceEphemerals.collect {
      case (eid: EphemeralId, _: Composite) if implements(eid).contains(id) =>
        eid
    }.toList
  }

  def enumFields(composite: Composite): List[ExtendedField] = {
    composite.flatMap(i => enumFields(typespace(toKey(i)))).toList
  }

  def enumFields(defn: FinalDefinition): List[ExtendedField] = {
    val fields = defn match {
      case t: Interface =>
        val superFields = enumFields(t.interfaces)
          .map(_.copy(definedBy = t.id)) // in fact super field is defined by this

        val thisFields = toExtendedFields(t.fields, t.id)
        superFields ++ thisFields
      case t: DTO =>
        enumFields(t.interfaces)

      case t: Identifier =>
        toExtendedFields(t.fields, t.id)

      case _: Enumeration =>
        List()

      case _: Alias =>
        List()
    }

    fields.distinct
  }

  private def toExtendedFields(fields: Aggregate, id: TypeId) = {
    fields.map(f => ExtendedField(f, id: TypeId)).toList
  }

  //  def fetchFields(composite: Composite): List[Field] = {
  //    enumFields(composite).map(_.field)
  //  }

  //  def fetchFields(defn: FinalDefinition): List[Field] = {
  //    enumFields(defn).map(_.field)
  //  }

  // TODO: do we need this?
  def explode(defn: Field): List[TrivialField] = {
    defn.typeId match {
      case t: Primitive =>
        List(TrivialField(t, defn.name))
      case t: UserType =>
        explode(typespace(t))
    }
  }

  def explode(defn: FinalDefinition): List[TrivialField] = {
    defn match {
      case t: Interface =>
        t.interfaces.flatMap(i => explode(typespace(toKey(i)))).toList ++ t.fields.flatMap(explode).toList

      case t: DTO =>
        t.interfaces.flatMap(i => explode(typespace(toKey(i)))).toList

      case t: Identifier =>
        t.fields.flatMap(explode).toList

      case _: Alias =>
        List()

      case _: Enumeration =>
        List()
    }
  }

}

object Typespace {

  trait Dependency {
    def definedIn: TypeId

    def typeId: TypeId
  }

  object Dependency {

    case class Field(definedIn: TypeId, tpe: common.Field) extends Dependency {

      override def typeId: TypeId = tpe.typeId

      override def toString: TypeName = s"[field $definedIn::${tpe.name} :$typeId]"
    }

    case class Parameter(definedIn: TypeId, typeId: TypeId) extends Dependency {
      override def toString: TypeName = s"[param $definedIn::$typeId]"
    }


    case class Interface(definedIn: TypeId, typeId: InterfaceId) extends Dependency {
      override def toString: TypeName = s"[interface $definedIn::$typeId]"
    }

    case class Alias(definedIn: TypeId, typeId: TypeId) extends Dependency {
      override def toString: TypeName = s"[alias $definedIn::$typeId]"
    }

  }

}
