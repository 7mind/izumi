package com.github.pshirshov.izumi.idealingua.model.il

import com.github.pshirshov.izumi.idealingua.model.common
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{DTOId, EphemeralId, InterfaceId, ServiceId}
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.DefMethod.RPCMethod
import com.github.pshirshov.izumi.idealingua.model.il.FinalDefinition._
import com.github.pshirshov.izumi.idealingua.model.il.Typespace.Dependency


class Typespace(original: DomainDefinition) {
  val domain: DomainDefinition = DomainDefinition.normalizeTypeIds(original)

  type KeyType = UserType

  protected val referenced: Map[DomainId, Typespace] = domain.referenced.mapValues(d => new Typespace(d))
  protected val typespace: Map[KeyType, FinalDefinition] = verified(domain.types)
  protected val services: Map[ServiceId, Service] = domain.services.groupBy(_.id).mapValues(_.head)

  protected val serviceEphemerals: Map[KeyType, DTO] = (for {
    service <- services.values
    method <- service.methods
  } yield {
    method match {
      case m: RPCMethod =>
        val inId = EphemeralId(service.id, s"In${m.name.capitalize}")
        val outId = EphemeralId(service.id, s"Out${m.name.capitalize}")
        val inIid = DTOId(inId.pkg, inId.name)
        val outIid = DTOId(outId.pkg, outId.name)

        Seq(
          toKey(inId) -> DTO(inIid, m.signature.input, List.empty)
          , toKey(outId) -> DTO(outIid, m.signature.output, List.empty)
        )
    }
  }).flatten.toMap

  protected val interfaceEphemerals: Map[KeyType, DTO] = {
    typespace
      .values
      .collect {
        case i: Interface =>
          val eid = EphemeralId(i.id, toDtoName(i.id))
          val iid = DTOId(eid.pkg, eid.name)
          toKey(eid) -> DTO(iid, List(i.id), List.empty)
      }
      .toMap
  }

  def apply(id: TypeId): FinalDefinition = {
    val typeDomain = domain.id.toDomainId(id)
    if (domain.id == typeDomain) {
      id match {
        case e if serviceEphemerals.contains(toKey(e)) =>
          serviceEphemerals(toKey(e))

        case e if interfaceEphemerals.contains(toKey(e)) =>
          interfaceEphemerals(toKey(e))

        case o =>
          typespace(toKey(o))
      }
    } else {
      referenced(typeDomain).apply(id)
    }
  }

  protected def apply(id: InterfaceId): Interface = apply(id: TypeId).asInstanceOf[Interface]

  def apply(id: ServiceId): Service = services(id)

  def toDtoName(id: TypeId): String = {
    id match {
      case _: InterfaceId =>
        s"${id.name}Impl"
      case _ =>
        s"${id.name}"

    }
  }

  def getComposite(id: TypeId): Composite = {
    apply(id) match {
      case i: Interface =>
        i.interfaces ++ i.concepts
      case i: DTO =>
        i.interfaces ++ i.concepts
      case _ =>
        throw new IDLException(s"Interface or DTO expected: $id")
    }
  }

  def ephemeralImplementors(id: InterfaceId): List[InterfaceConstructors] = {
    val compatibleIfs = compatible(id)
    val implementors = implementingDtos(id) ++ implementingEphemerals(id)

    val ifaceFields = enumFields(apply(id))
    val ifaceConflicts = FieldConflicts(ifaceFields)
    val ifaceNonUniqueFields = ifaceConflicts.softConflicts.keySet
    val fieldsToCopyFromInterface = ifaceFields.map(_.field)
      .toSet
      .filterNot(f => ifaceNonUniqueFields.contains(f.name))


    implementors.map {
      typeToConstruct =>
        val definition = apply(typeToConstruct)
        val implFields = enumFields(definition)

        val requiredParameters = implFields
          .map(_.definedBy)
          .collect({ case i: InterfaceId => i })
          .filterNot(compatibleIfs.contains)
          .toSet

        //println(id, typeToConstruct, missingInterfaces, allParents, requiredParameters, implFields)

        val fieldsToTakeFromParameters = requiredParameters
          .flatMap(mi => enumFields(apply(mi)))
          .filterNot(f => fieldsToCopyFromInterface.contains(f.field))
          .filterNot(f => ifaceNonUniqueFields.contains(f.field.name))

        InterfaceConstructors(
          typeToConstruct
          , requiredParameters.toList
          , fieldsToCopyFromInterface
          , fieldsToTakeFromParameters
          , ifaceConflicts
        )
    }
  }


  def allTypes: List[TypeId] = List(
    typespace.keys
    , serviceEphemerals.keys
    , interfaceEphemerals.keys
  ).flatten


  def all: List[TypeId] = List(
    allTypes
    , services.values.map(_.id)
    , domain.types.collect({ case t: Enumeration => t })
      .flatMap(e => e.members.map(m => EphemeralId(e.id, m)))
  ).flatten

  def extractDependencies(definition: FinalDefinition): Seq[Dependency] = {
    definition match {
      case _: Enumeration =>
        Seq.empty
      case d: Interface =>
        d.interfaces.map(i => Dependency.Interface(d.id, i)) ++
          d.concepts.flatMap(c => extractDependencies(apply(c))) ++
          d.fields.map(f => Dependency.Field(d.id, f))
      case d: DTO =>
        d.interfaces.map(i => Dependency.Interface(d.id, i))
      case d: Identifier =>
        d.fields.map(f => Dependency.Field(d.id, f))
      case d: Adt =>
        d.alternatives.map(apply).flatMap(extractDependencies)
      case d: Alias =>
        Seq(Dependency.Alias(d.id, d.target))
    }
  }

  def verify(): Unit = {
    import Typespace._
    val typeDependencies = domain.types.flatMap(extractDependencies)

    val serviceDependencies = for {
      service <- services.values
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
      .filterNot(d => referenced.get(domain.id.toDomainId(d.typeId)).exists(_.typespace.contains(toKey(d.typeId))))

    if (missingTypes.nonEmpty) {
      throw new IDLException(s"Incomplete typespace: $missingTypes")
    }
  }

  def toKey(typeId: TypeId): KeyType = {
    if (typeId.pkg.isEmpty) {
      UserType(domain.id.toPackage, typeId.name)
    } else {
      UserType(typeId)
    }
  }

  def parents(id: TypeId): List[InterfaceId] = {
    id match {
      case i: InterfaceId =>
        val defn = apply(i)
        List(i) ++ defn.interfaces.flatMap(parents)

      case i: DTOId =>
        apply(i).interfaces.flatMap(parents)

      case e if serviceEphemerals.contains(toKey(e)) =>
        serviceEphemerals(toKey(e)).interfaces.flatMap(parents)

      case e if interfaceEphemerals.contains(toKey(e)) =>
        interfaceEphemerals(toKey(e)).interfaces.flatMap(parents)

      case u: UserType =>
        parents(apply(u).id)

      case _ =>
        throw new IDLException(s"Unexpected id: $id")
    }
  }

  def compatible(id: TypeId): List[InterfaceId] = {
    id match {
      case i: InterfaceId =>
        val defn = apply(i)
        List(i) ++ defn.interfaces.flatMap(compatible) ++ defn.concepts.flatMap(compatible)

      case i: DTOId =>
        apply(i).interfaces.flatMap(compatible)

      case e if serviceEphemerals.contains(toKey(e)) =>
        serviceEphemerals(toKey(e)).interfaces.flatMap(compatible)

      case e if interfaceEphemerals.contains(toKey(e)) =>
        interfaceEphemerals(toKey(e)).interfaces.flatMap(compatible)

      case u: UserType =>
        compatible(apply(u).id)

      case _ =>
        throw new IDLException(s"Unexpected id: $id")
    }
  }

  protected def implementingDtos(id: InterfaceId): List[DTOId] = {
    typespace.collect {
      case (tid, _: DTO) if parents(tid).contains(id) || compatible(tid).contains(id) =>
        tid.toDTO
    }.toList
  }

  protected def implementingEphemerals(id: InterfaceId): List[UserType] = {
    serviceEphemerals.collect {
      case (eid, _: DTO) if parents(eid).contains(id) || compatible(eid).contains(id) =>
        eid
    }.toList
  }

  def enumFields(defn: FinalDefinition): List[ExtendedField] = {
    val fields = defn match {
      case t: Interface =>
        val superFields = enumFields(t.interfaces)
          .map(_.copy(definedBy = t.id)) // in fact super field is defined by this

        val embeddedFields = t.concepts.flatMap(id => enumFields(apply(id)))

        val thisFields = toExtendedFields(t.fields, t.id)
        superFields ++ thisFields ++ embeddedFields

      case t: DTO =>
        enumFields(t.interfaces) ++ enumFields(t.concepts)

      case t: Adt =>
        t.alternatives.map(apply).flatMap(enumFields)

      case t: Identifier =>
        toExtendedFields(t.fields, t.id)

      case _: Enumeration =>
        List()

      case _: Alias =>
        List()
    }

    fields.distinct
  }

  def enumFields(composite: Composite): List[ExtendedField] = {
    composite.flatMap(i => enumFields(typespace(toKey(i))))
  }

  def sameSignature(tid: TypeId): List[DTO] = {
    val sig = signature(apply(tid))

    allTypes
      .filterNot {
        case e: EphemeralId => e.parent == tid
        case id => id == tid
      }
      .map(apply)
      .collect({ case e: DTO => e })
      .filter(another => signature(another) == sig)
      .filterNot {
        id =>
          toKey(tid) == toKey(id.id) && parents(id.id).map(toKey).contains(toKey(tid))
      }
      .distinct
  }

  protected def signature(defn: FinalDefinition): List[Field] = {
    enumFields(defn).map(_.field).sortBy(_.name)
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

  protected def apply(id: DTOId): DTO = {
    apply(id: TypeId).asInstanceOf[DTO]
  }

  protected def toExtendedFields(fields: Aggregate, id: TypeId): List[ExtendedField] = {
    fields.map(f => ExtendedField(f, id: TypeId))
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
