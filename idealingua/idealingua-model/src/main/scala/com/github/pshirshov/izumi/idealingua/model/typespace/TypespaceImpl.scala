package com.github.pshirshov.izumi.idealingua.model.typespace

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod.{DeprecatedRPCMethod, RPCMethod}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._
import com.github.pshirshov.izumi.idealingua.model.typespace.structures.{ConverterDef, PlainStruct, Struct}



class TypespaceImpl(val domain: DomainDefinition) extends Typespace with Resolver {
  protected[typespace] val types: TypeCollection = new TypeCollection(domain)

  protected val referenced: Map[DomainId, Typespace] = domain.referenced.mapValues(d => new TypespaceImpl(d))
  protected val index: Map[TypeId, TypeDef] = types.index


  override def inheritance: Inheritance = new InheritanceImpl(this, types)

  def apply(id: ServiceId): Service = {
    types.services(id)
  }

  def apply(id: TypeId): TypeDef = {
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

  def toDtoName(id: TypeId): String = types.toDtoName(id)


  def implementors(id: InterfaceId): List[ConverterDef] = {
    val implementors = inheritance.implementingDtos(id)
    compatibleImplementors(implementors, id)
  }

  def compatibleImplementors(id: InterfaceId): List[ConverterDef] = {
    val implementors = compatibleDtos(id)
    compatibleImplementors(implementors, id)
  }


  override def requiredInterfaces(s: Struct): List[InterfaceId] = {
    s.all
      .map(_.definedBy)
      .collect({ case i: InterfaceId => i })
      .distinct
  }

  def verify(): Unit = {
    val typeDependencies = domain.types.flatMap(extractDependencies)

    val serviceDependencies = for {
      service <- types.services.values
      method <- service.methods
    } yield {
      method match {
        case m: DeprecatedRPCMethod =>
          (m.signature.input ++ m.signature.output).map(i => DefinitionDependency.DepServiceParameter(service.id, i))
        case m: RPCMethod =>
          Seq() // TODO
      }
    }

    val allDependencies = typeDependencies ++ serviceDependencies.flatten


    // TODO: very ineffective!
    val missingTypes = allDependencies
      .filterNot(_.typeId.isInstanceOf[Builtin])
      .filterNot(d => types.index.contains(d.typeId))
      .filterNot(d => referenced.get(domain.id.toDomainId(d.typeId)).exists(t => t.types.index.contains(d.typeId)))

    if (missingTypes.nonEmpty) {
      throw new IDLException(s"Incomplete typespace: $missingTypes")
    }
  }


  def structure(defn: IdentifierId): PlainStruct = {
    structure(get(defn))
  }

  def structure(defn: Identifier): PlainStruct = {
    PlainStruct(extractFields(defn))
  }

  def structure(defn: WithStructure): Struct = {
    val parts = apply(defn.id) match {
      case i: Interface =>
        i.struct.superclasses
      case i: DTO =>
        i.struct.superclasses
    }

    Struct(defn.id, parts, extractFields(defn))
  }

  def enumFields(id: StructureId): Struct = {
    structure(get(id))
  }

  def sameSignature(tid: StructureId): List[DTO] = {
    val sig = signature(get(tid))

    types
      .structures
      .filterNot(_.id == tid)
      .filter(another => sig == signature(another))
      .filterNot(_.id == tid)
      .distinct
      .filterNot(id => inheritance.parentsInherited(id.id).contains(tid))
      .collect({ case t: DTO => t })
      .toList
  }



  protected def compatibleDtos(id: InterfaceId): List[DTOId] = {
    index.collect {
      case (tid, d: DTO) if inheritance.parentsStructural(tid).contains(id) =>
        d.id
    }.toList
  }


  protected def extractFields(defn: TypeDef): List[ExtendedField] = {
    val fields = defn match {
      case t: Interface =>
        val superFields = compositeFields(t.struct.superclasses.interfaces)
        val embeddedFields = t.struct.superclasses.concepts.flatMap(id => extractFields(apply(id)))
        val thisFields = toExtendedFields(t.struct.fields, t.id)

        superFields.map(_.copy(definedBy = t.id)) ++ // in fact super field is defined by this
          embeddedFields ++
          thisFields

      case t: DTO =>
        val superFields = compositeFields(t.struct.superclasses.interfaces)
        val embeddedFields = t.struct.superclasses.concepts.flatMap(id => extractFields(apply(id)))
        val thisFields = toExtendedFields(t.struct.fields, t.id)

        superFields ++
          embeddedFields ++
          thisFields

      case t: Adt =>
        t.alternatives.map(apply).flatMap(extractFields)

      case t: Identifier =>
        toExtendedPrimitiveFields(t.fields, t.id)

      case _: Enumeration =>
        List()

      case _: Alias =>
        List()
    }

    fields.distinct
  }

  protected def toExtendedFields(fields: Tuple, id: TypeId): List[ExtendedField] = {
    fields.map(f => ExtendedField(f, id: TypeId))
  }

  protected def toExtendedPrimitiveFields(fields: PrimitiveTuple, id: TypeId): List[ExtendedField] = {
    fields.map(f => ExtendedField(Field(f.typeId, f.name), id: TypeId))
  }

  protected def compositeFields(composite: Interfaces): List[ExtendedField] = {
    composite.flatMap(i => extractFields(index(i)))
  }


  protected def signature(defn: WithStructure): List[Field] = {
    structure(defn).all.map(_.field).sortBy(_.name)
  }


  protected def extractDependencies(definition: TypeDef): Seq[DefinitionDependency] = {
    definition match {
      case _: Enumeration =>
        Seq.empty
      case d: Interface =>
        d.struct.superclasses.interfaces.map(i => DefinitionDependency.DepInterface(d.id, i)) ++
          d.struct.superclasses.concepts.flatMap(c => extractDependencies(apply(c))) ++
          d.struct.fields.map(f => DefinitionDependency.DepField(d.id, f.typeId, f))
      case d: DTO =>
        d.struct.superclasses.interfaces.map(i => DefinitionDependency.DepInterface(d.id, i)) ++
          d.struct.superclasses.concepts.flatMap(c => extractDependencies(apply(c))) ++
          d.struct.fields.map(f => DefinitionDependency.DepField(d.id, f.typeId, f))

      case d: Identifier =>
        d.fields.map(f => DefinitionDependency.DepPrimitiveField(d.id, f.typeId, f))

      case d: Adt =>
        d.alternatives.map(apply).flatMap(extractDependencies)

      case d: Alias =>
        Seq(DefinitionDependency.DepAlias(d.id, d.target))
    }
  }

  protected def compatibleImplementors(implementors: List[StructureId], id: InterfaceId): List[ConverterDef] = {
    val struct = structure(get(id))
    val parentInstanceFields = struct.unambigious.map(_.field).toSet

    implementors
      .map(t => structure(get(t)))
      .map {
        istruct =>
          val localFields = istruct.localOrAmbigious
            .map(_.field)
            .toSet

          val filteredParentFields = parentInstanceFields.diff(localFields)

          val mixinInstanceFields = istruct
            .unambigiousInherited
            .map(_.definedBy)
            .collect({ case i: InterfaceId => i })
            .flatMap(mi => structure(get(mi)).all)
            .filterNot(f => parentInstanceFields.contains(f.field))
            .filterNot(f => localFields.contains(f.field))
            .toSet


          // TODO: pass definition instead of id
          ConverterDef(
            istruct.id
            , filteredParentFields
            , localFields
            , mixinInstanceFields
          )
      }
  }

}



