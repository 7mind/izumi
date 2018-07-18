package com.github.pshirshov.izumi.idealingua.model.typespace

import com.github.pshirshov.izumi.idealingua.model.common.{ExtendedField, FieldDef, StructureId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._

private class FieldExtractor(resolver: TypeResolver, user: TypeId) {
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
    composite.flatMap(i => extractFields(resolver.apply(i), nextDepth))
  }

  protected def toExtendedFields(nextDepth: Int, fields: Tuple, id: TypeId): List[ExtendedField] = {
    fields.zipWithIndex.map {
      case (f, idx) =>
        ExtendedField(f, FieldDef(id, idx, user, nextDepth - 1))
    }
  }

  protected def toExtendedPrimitiveFields(nextDepth: Int, fields: IdTuple, id: TypeId): List[ExtendedField] = {
    fields.zipWithIndex.map {
      case (f, idx) =>
        ExtendedField(Field(f.typeId, f.name), FieldDef(id, idx, user, nextDepth - 1))
    }
  }

  private def filterFields(nextDepth: Int, id: StructureId, superFields: List[ExtendedField], struct: Structure): List[ExtendedField] = {
    val embeddedFields = struct.superclasses.concepts.map(resolver.apply).flatMap(extractFields(_, nextDepth))
    val thisFields = toExtendedFields(nextDepth, struct.fields, id)

    val removable = embeddedFields ++ thisFields

    val defn = resolver.apply(id)
    val removedFields = extractRemoved(defn).toSet

    val badRemovals = superFields.map(_.field).toSet.intersect(removedFields)
    if (badRemovals.nonEmpty) {
      throw new IDLException(s"Cannot remove inherited fields from $id: $badRemovals")
    }

    superFields ++ removable.filterNot(f => removedFields.contains(f.field))
  }

  protected def extractRemoved(defn: TypeDef): List[Field] = {
    val fields = defn match {
      case t: Interface =>
        t.struct.removedFields ++ t.struct.superclasses.removedConcepts.map(resolver.apply).flatMap(extractFields).map(_.field)

      case t: DTO =>
        t.struct.removedFields ++ t.struct.superclasses.removedConcepts.map(resolver.apply).flatMap(extractFields).map(_.field)

      case _ =>
        List()
    }

    fields.distinct
  }
}
