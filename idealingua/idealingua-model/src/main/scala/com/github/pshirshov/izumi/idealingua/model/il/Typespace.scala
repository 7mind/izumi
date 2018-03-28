package com.github.pshirshov.izumi.idealingua.model.il

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ILAst.Service.DefMethod._
import com.github.pshirshov.izumi.idealingua.model.il.ILAst._
import com.github.pshirshov.izumi.idealingua.model.il.Typespace.Dependency


class Typespace(val domain: DomainDefinition) {
  protected val referenced: Map[DomainId, Typespace] = domain.referenced.mapValues(d => new Typespace(d))
  protected val types = new TypeCollection(domain)
  protected val index: Map[TypeId, ILAst] = types.index


  def apply(id: TypeId): ILAst = {
    val typeDomain = domain.id.toDomainId(id)
    if (domain.id == typeDomain) {
      id match {
        case o =>
          index(o)
      }
    } else {
      referenced(typeDomain).apply(id)
    }
  }

  protected def apply(id: InterfaceId): Interface = apply(id: TypeId).asInstanceOf[Interface]

  def apply(id: ServiceId): Service = types.services(id)

  def toDtoName(id: TypeId): String = types.toDtoName(id)


  def implementors(id: InterfaceId): List[InterfaceConstructors] = {
    val implementors = implementingDtos(id) //++ implementingEphemerals(id)
    compatibleImplementors(implementors, id)
  }

  def compatibleImplementors(id: InterfaceId): List[InterfaceConstructors] = {
    val implementors = compatibleDtos(id) //++ compatibleEphemerals(id)
    compatibleImplementors(implementors, id)
  }

  private def allTypes: List[TypeId] = types.all.map(_.id).toList


  def all: Set[TypeId] = List(
    allTypes
    , types.services.values.map(_.id)
  )
    .flatten
    .toSet

  def verify(): Unit = {
    import Typespace._
    val typeDependencies = domain.types.flatMap(extractDependencies)

    val serviceDependencies = for {
      service <- types.services.values
      method <- service.methods
    } yield {
      method match {
        case m: RPCMethod =>
          (m.signature.input ++ m.signature.output).map(i => Dependency.Parameter(service.id, i))
      }
    }

    val allDependencies = typeDependencies ++ serviceDependencies.flatten


    // TODO: very ineffective!
    val missingTypes = allDependencies
      .filterNot(_.typeId.isInstanceOf[Builtin])
      .filterNot(d => all.contains(d.typeId))
      .filterNot(d => referenced.get(domain.id.toDomainId(d.typeId)).exists(_.all.contains(d.typeId)))

    if (missingTypes.nonEmpty) {
      throw new IDLException(s"Incomplete typespace: $missingTypes")
    }
  }

  def parents(id: TypeId): List[InterfaceId] = {
    id match {
      case i: InterfaceId =>
        val defn = apply(i)
        List(i) ++ defn.interfaces.flatMap(parents)

      case i: DTOId =>
        apply(i).interfaces.flatMap(parents)

      case _: IdentifierId =>
        List()

      case _: EnumId =>
        List()

      case _: AliasId =>
        List()

      case _: AdtId =>
        List()

      case e: Builtin =>
        throw new IDLException(s"Unexpected id: $e")

      case e: ServiceId =>
        throw new IDLException(s"Unexpected id: $e")

    }
  }

  def compatible(id: TypeId): List[InterfaceId] = {
    id match {
      case i: InterfaceId =>
        val defn = apply(i)
        List(i) ++ defn.interfaces.flatMap(compatible) ++ defn.concepts.flatMap(compatible)

      case i: DTOId =>
        apply(i).interfaces.flatMap(compatible)

      case _: IdentifierId =>
        List()

      case _: EnumId =>
        List()

      case _: AliasId =>
        List()

      case _: AdtId =>
        List()

      case e: Builtin =>
        throw new IDLException(s"Unexpected id: $e")

      case e: ServiceId =>
        throw new IDLException(s"Unexpected id: $e")

    }
  }

  protected def implementingDtos(id: InterfaceId): List[DTOId] = {
    index.collect {
      case (tid, d: DTO) if parents(tid).contains(id) =>
        d.id
    }.toList
  }

  protected def compatibleDtos(id: InterfaceId): List[DTOId] = {
    index.collect {
      case (tid, d: DTO) if compatible(tid).contains(id) =>
        d.id
    }.toList
  }

  def enumFields(defn: ILAst): Struct = {
    Struct(extractFields(defn), null)
  }

  def enumFields(id: TypeId): Struct = {
    enumFields(getComposite(id))
  }

  protected def enumFields(composite: Composite): Struct = {
    Struct(extractFields(composite), composite)
  }

  def sameSignature(tid: TypeId): List[DTO] = {
    val ret = filterTypes(tid) {
      case (thisSig, otherSig) =>
        thisSig == otherSig
    }
      .filterNot(id => parents(id._1).contains(tid))
      .collect({ case (t, e: DTO) => t -> e })

    ret.map(_._2)
  }

  protected def getComposite(id: TypeId): Composite = {
    apply(id) match {
      case i: Interface =>
        i.interfaces ++ i.concepts
      case i: DTO =>
        i.interfaces ++ i.concepts
      case _ =>
        throw new IDLException(s"Interface or DTO expected: $id")
    }
  }

  protected def extractFields(defn: ILAst): List[ExtendedField] = {
    val fields = defn match {
      case t: Interface =>
        val superFields = extractFields(t.interfaces)
          .map(_.copy(definedBy = t.id)) // in fact super field is defined by this

        val embeddedFields = t.concepts.flatMap(id => extractFields(apply(id)))

        val thisFields = toExtendedFields(t.fields, t.id)
        superFields ++ thisFields ++ embeddedFields

      case t: DTO =>
        extractFields(t.interfaces) ++ extractFields(t.concepts)

      case t: Adt =>
        t.alternatives.map(apply).flatMap(extractFields)

      case t: Identifier =>
        toExtendedFields(t.fields, t.id)

      case _: Enumeration =>
        List()

      case _: Alias =>
        List()
    }

    fields.distinct
  }

  protected def extractFields(composite: Composite): List[ExtendedField] = {
    composite.flatMap(i => extractFields(index(i)))
  }

  protected def filterTypes(tid: TypeId)(pred: (List[ILAst.Field], List[ILAst.Field]) => Boolean): List[(TypeId, ILAst)] = {
    val sig = signature(apply(tid))

    allTypes
      .filterNot(_ == tid)
      .map(id => id -> apply(id))
      .filter(another => pred(sig, signature(another._2)))
      .filterNot {
        id =>
          tid == id._1 || tid == id._2.id
      }
      .distinct
  }

  protected def signature(defn: ILAst): List[Field] = {
    enumFields(defn).all.map(_.field)
  }


  protected def apply(id: DTOId): DTO = {
    apply(id: TypeId).asInstanceOf[DTO]
  }

  protected def toExtendedFields(fields: Aggregate, id: TypeId): List[ExtendedField] = {
    fields.map(f => ExtendedField(f, id: TypeId))
  }

  protected def extractDependencies(definition: ILAst): Seq[Dependency] = {
    definition match {
      case _: Enumeration =>
        Seq.empty
      case d: Interface =>
        d.interfaces.map(i => Dependency.Interface(d.id, i)) ++
          d.concepts.flatMap(c => extractDependencies(apply(c))) ++
          d.fields.map(f => Dependency.Field(d.id, f.typeId, f))
      case d: DTO =>
        d.interfaces.map(i => Dependency.Interface(d.id, i))
      case d: Identifier =>
        d.fields.map(f => Dependency.Field(d.id, f.typeId, f))
      case d: Adt =>
        d.alternatives.map(apply).flatMap(extractDependencies)
      case d: Alias =>
        Seq(Dependency.Alias(d.id, d.target))
    }
  }

  protected def compatibleImplementors(implementors: List[TypeId], id: InterfaceId): List[InterfaceConstructors] = {
    val ifaceFields = enumFields(apply(id))
    val ifaceNonUniqueFields = ifaceFields.conflicts.softConflicts.keySet
    val fieldsToCopyFromInterface = ifaceFields.all.map(_.field)
      .toSet
      .filterNot(f => ifaceNonUniqueFields.contains(f.name))

    val compatibleIfs = compatible(id)

    implementors.map {
      typeToConstruct =>
        val definition = apply(typeToConstruct)
        val implFields = enumFields(definition).all

        val requiredParameters = implFields
          .map(_.definedBy)
          .collect({ case i: InterfaceId => i })
          .filterNot(compatibleIfs.contains)
          .toSet

        val fieldsToTakeFromParameters = requiredParameters
          .flatMap(mi => enumFields(apply(mi)).all)
          .filterNot(f => fieldsToCopyFromInterface.contains(f.field))
          .filterNot(f => ifaceNonUniqueFields.contains(f.field.name))

        // TODO: pass definition instead of id
        InterfaceConstructors(
          typeToConstruct
          , requiredParameters.toList
          , fieldsToCopyFromInterface
          , fieldsToTakeFromParameters
          , ifaceFields
        )
    }
  }

}


object Typespace {

  trait Dependency {
    def definedIn: TypeId

    def typeId: TypeId
  }

  object Dependency {

    case class Field(definedIn: TypeId, definite: TypeId, tpe: ILAst.Field) extends Dependency {

      override def typeId: TypeId = definite

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
