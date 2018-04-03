package com.github.pshirshov.izumi.idealingua.model.typespace

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{IdentifierId, InterfaceId}
import com.github.pshirshov.izumi.idealingua.model.common.{ExtendedField, StructureId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._
import com.github.pshirshov.izumi.idealingua.model.typespace.structures.{ConverterDef, PlainStruct, Struct}

protected[typespace] class StructuralQueriesImpl(types: TypeCollection, resolver: TypeResolver, inheritance: InheritanceQueries) extends StructuralQueries {
  def structure(defn: IdentifierId): PlainStruct = {
    structure(resolver.get(defn))
  }

  def structure(defn: Identifier): PlainStruct = {
    PlainStruct(extractFields(defn))
  }

  def structure(id: StructureId): Struct = {
    structure(resolver.get(id))
  }

  def structure(defn: WithStructure): Struct = {
    val parts = resolver.get(defn.id) match {
      case i: Interface =>
        i.struct.superclasses
      case i: DTO =>
        i.struct.superclasses
    }

    Struct(defn.id, parts, extractFields(defn))
  }

  def conversions(id: InterfaceId): List[ConverterDef] = {
    val implementors = inheritance.compatibleDtos(id)
    converters(implementors, id)
  }


  override def structuralParents(interface: Interface): List[Struct] = {
    val thisStructure = structure(interface)

    // we don't add explicit parents here because their converters are available
    val allStructuralParents = List(interface.id) ++ interface.struct.superclasses.concepts

    allStructuralParents
      .distinct
      .map(structure)
      .filter(_.all.map(_.field).diff(thisStructure.all.map(_.field)).isEmpty)
  }

  def sameSignature(tid: StructureId): List[DTO] = {
    val sig = signature(resolver.get(tid))

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

  protected[typespace] def converters(implementors: List[StructureId], id: InterfaceId): List[ConverterDef] = {
    val struct = structure(resolver.get(id))
    val parentInstanceFields = struct.unambigious.map(_.field).toSet

    implementors
      .map(t => structure(resolver.get(t)))
      .map {
        istruct =>
          val localFields = istruct.localOrAmbigious
            .map(_.field)
            .toSet
          val all = istruct.all.map(_.field).toSet

          val filteredParentFields = parentInstanceFields.diff(localFields)

          val mixinInstanceFields = istruct
            .unambigiousInherited
            .map(_.definedBy)
            .collect({ case i: InterfaceId => i })
            .flatMap(mi => structure(resolver.get(mi)).all)
            .filter(f => all.contains(f.field)) // to drop removed fields
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

  protected def extractFields(defn: TypeDef): List[ExtendedField] = {
    val fields = defn match {
      case t: Interface =>
        val struct = t.struct
        val superFields = compositeFields(struct.superclasses.interfaces)
          .map(_.copy(definedBy = t.id)) // for interfaces super field is ok to consider as defined by this interface
        filterFields(t.id, superFields, struct)

      case t: DTO =>
        val struct = t.struct
        val superFields = compositeFields(struct.superclasses.interfaces)
        filterFields(t.id, superFields, struct)

      case t: Adt =>
        t.alternatives.map(_.typeId).map(resolver.apply).flatMap(extractFields)

      case t: Identifier =>
        toExtendedPrimitiveFields(t.fields, t.id)

      case _: Enumeration =>
        List()

      case _: Alias =>
        List()
    }

    fields.distinct
  }


  private def filterFields(id: StructureId, superFields: List[ExtendedField], struct: Structure): List[ExtendedField] = {
    val embeddedFields = struct.superclasses.concepts.map(resolver.apply).flatMap(extractFields)
    val thisFields = toExtendedFields(struct.fields, id)

    val removable = embeddedFields ++ thisFields

    val removedFields = extractRemoved(resolver.apply(id)).toSet

    val badRemovals = superFields.map(_.field).toSet.intersect(removedFields)
    if (badRemovals.nonEmpty) {
      throw new IDLException(s"Cannot remove inherited fields from $id: $badRemovals")
    }

    superFields ++ removable.filterNot(f => removedFields.contains(f.field))
  }

  protected def extractRemoved(defn: TypeDef): List[Field] = {
    val fields = defn match {
      case t: Interface =>
        t.struct.removedFields ++ t.struct.superclasses.removedConcepts.map(resolver.apply).flatMap(extractRemoved)

      case t: DTO =>
        t.struct.removedFields ++ t.struct.superclasses.removedConcepts.map(resolver.apply).flatMap(extractRemoved)

      case _ =>
        List()
    }

    fields.distinct
  }

  protected def compositeFields(composite: Interfaces): List[ExtendedField] = {
    composite.flatMap(i => extractFields(types.index(i)))
  }

  protected def toExtendedFields(fields: Tuple, id: TypeId): List[ExtendedField] = {
    fields.map(f => ExtendedField(f, id: TypeId))
  }

  protected def toExtendedPrimitiveFields(fields: PrimitiveTuple, id: TypeId): List[ExtendedField] = {
    fields.map(f => ExtendedField(Field(f.typeId, f.name), id: TypeId))
  }

  protected def signature(defn: WithStructure): List[Field] = {
    structure(defn).all.map(_.field).sortBy(_.name)
  }
}
