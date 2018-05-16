package com.github.pshirshov.izumi.idealingua.model.typespace

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{IdentifierId, InterfaceId}
import com.github.pshirshov.izumi.idealingua.model.common.{ExtendedField, FieldDef, StructureId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._
import com.github.pshirshov.izumi.idealingua.model.typespace.structures.{ConverterDef, FieldConflicts, PlainStruct, Struct}

protected[typespace] class StructuralQueriesImpl(types: TypeCollection, resolver: TypeResolver, inheritance: InheritanceQueries) extends StructuralQueries {
  def structure(defn: IdentifierId): PlainStruct = {
    structure(resolver.get(defn))
  }

  def structure(defn: Identifier): PlainStruct = {
    val extractor = new FieldExtractor(types, resolver, defn.id)
    PlainStruct(extractor.extractFields(defn))
  }

  def structure(id: StructureId): Struct = {
    structure(resolver.get(id))
  }

  def structure(defn: WithStructure): Struct = {
    val extractor = new FieldExtractor(types, resolver, defn.id)

    val parts = resolver.get(defn.id) match {
      case i: Interface =>
        i.struct.superclasses
      case i: DTO =>
        i.struct.superclasses
    }


    val all = extractor.extractFields(defn)

    if (defn.id.path.domain.toString.contains("inheritance")) {
      println(all.sortBy(_.defn.distance).mkString("\n  "))
    }

    val conflicts = all
      .groupBy(_.field.name)

    val (goodFields: Map[String, Seq[ExtendedField]], conflictingFields) = conflicts.partition(_._2.lengthCompare(1) == 0)

    val (softConflicts: Map[String, Map[Field, Seq[ExtendedField]]], hardConflicts: Map[String, Map[Field, Seq[ExtendedField]]]) = conflictingFields
      .map(kv => (kv._1, kv._2.groupBy(_.field)))
      .partition(_._2.size == 1)

    val allConflicts = FieldConflicts(all, goodFields, softConflicts, hardConflicts)

    // TODO: shitty side effect
    if (allConflicts.hardConflicts.nonEmpty) {
      throw new IDLException(s"Conflicting fields: ${allConflicts.hardConflicts}")
    }

    val unambigious: List[ExtendedField] = allConflicts.goodFields.flatMap(_._2).toList

    val ambigious: List[ExtendedField] = allConflicts.softConflicts.flatMap(_._2).map(_._2.head).toList

    val output = new Struct(defn.id, parts, unambigious, ambigious)

    val conflictsLeft = output.all.groupBy(_.field.name).filter(_._2.size > 1)
    if (conflictsLeft.nonEmpty) {
      throw new IDLException(s"IDL compiler bug. Field resolution failed: $conflictsLeft")
    }

    output
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
            .map(_.defn.definedBy)
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

  protected def signature(defn: WithStructure): List[Field] = {
    structure(defn).all.map(_.field).sortBy(_.name)
  }
}

private class FieldExtractor(types: TypeCollection, resolver: TypeResolver, user: TypeId) {
  def extractFields(defn: TypeDef): List[ExtendedField] = {
    extractFields(defn, 0)
  }

  protected def extractFields(defn: TypeDef, depth: Int): List[ExtendedField] = {
    val nextDepth = depth + 1
    val fields = defn match {
      case t: Interface =>
        val struct = t.struct
        val superFields = compositeFields(nextDepth, struct.superclasses.interfaces)
        //.map(_.copy(definedBy = t.id)) // for interfaces super field is ok to consider as defined by this interface
        filterFields(nextDepth, t.id, superFields, struct)

      case t: DTO =>
        val struct = t.struct
        val superFields = compositeFields(nextDepth, struct.superclasses.interfaces)
        filterFields(nextDepth, t.id, superFields, struct)

      case t: Adt =>
        t.alternatives.map(_.typeId).map(resolver.apply).flatMap(extractFields(_, nextDepth))

      case t: Identifier =>
        toExtendedPrimitiveFields(nextDepth, t.fields, t.id)

      case _: Enumeration =>
        List()

      case _: Alias =>
        List()
    }

    fields.distinct
  }

  protected def compositeFields(nextDepth: Int, composite: Interfaces): List[ExtendedField] = {
    composite.flatMap(i => extractFields(types.index(i), nextDepth))
  }

  protected def toExtendedFields(nextDepth: Int, fields: Tuple, id: TypeId): List[ExtendedField] = {
    fields.map(f => ExtendedField(f, FieldDef(id, user, nextDepth - 1)))
  }

  protected def toExtendedPrimitiveFields(nextDepth: Int, fields: PrimitiveTuple, id: TypeId): List[ExtendedField] = {
    fields.map(f => ExtendedField(Field(f.typeId, f.name), FieldDef(id, user, nextDepth - 1)))
  }

  private def filterFields(nextDepth: Int, id: StructureId, superFields: List[ExtendedField], struct: Structure): List[ExtendedField] = {
    val embeddedFields = struct.superclasses.concepts.map(resolver.apply).flatMap(extractFields(_, nextDepth))
    val thisFields = toExtendedFields(nextDepth, struct.fields, id)

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
}
