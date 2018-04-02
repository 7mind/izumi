package com.github.pshirshov.izumi.idealingua.model.typespace

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{IdentifierId, InterfaceId}
import com.github.pshirshov.izumi.idealingua.model.common.{ExtendedField, StructureId, TypeId}
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
    val parts = resolver.apply(defn.id) match {
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

          val filteredParentFields = parentInstanceFields.diff(localFields)

          val mixinInstanceFields = istruct
            .unambigiousInherited
            .map(_.definedBy)
            .collect({ case i: InterfaceId => i })
            .flatMap(mi => structure(resolver.get(mi)).all)
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
        val superFields = compositeFields(t.struct.superclasses.interfaces)
        val embeddedFields = t.struct.superclasses.concepts.flatMap(id => extractFields(resolver.apply(id)))
        val thisFields = toExtendedFields(t.struct.fields, t.id)

        superFields.map(_.copy(definedBy = t.id)) ++ // in fact super field is defined by this
          embeddedFields ++
          thisFields

      case t: DTO =>
        val superFields = compositeFields(t.struct.superclasses.interfaces)
        val embeddedFields = t.struct.superclasses.concepts.flatMap(id => extractFields(resolver.apply(id)))
        val thisFields = toExtendedFields(t.struct.fields, t.id)

        superFields ++
          embeddedFields ++
          thisFields

      case t: Adt =>
        t.alternatives.map(resolver.apply).flatMap(extractFields)

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
    composite.flatMap(i => extractFields(types.index(i)))
  }


  protected def signature(defn: WithStructure): List[Field] = {
    structure(defn).all.map(_.field).sortBy(_.name)
  }
}
