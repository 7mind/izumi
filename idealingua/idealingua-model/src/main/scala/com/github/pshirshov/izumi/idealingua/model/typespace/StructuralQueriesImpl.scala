package com.github.pshirshov.izumi.idealingua.model.typespace

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{DTOId, IdentifierId, InterfaceId}
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._
import com.github.pshirshov.izumi.idealingua.model.typespace.structures.{ConverterDef, FieldConflicts, PlainStruct, Struct}

import scala.collection.mutable

protected[typespace] class StructuralQueriesImpl(types: TypeCollection, resolver: TypeResolver, inheritance: InheritanceQueries, tools: TypespaceTools) extends StructuralQueries {
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

    val conflicts = findConflicts(all)

    val output = new Struct(defn.id, parts, conflicts.good, conflicts.soft)
    assert(output.all.groupBy(_.field.name).forall(_._2.size == 1), s"IDL Compiler Bug: contradictive structure for ${defn.id}: ${output.all.mkString("\n  ")}")
    output
  }

  private def findConflicts(all: List[ExtendedField]) = {
    val conflicts = all
      .groupBy(_.field.name)

    val goodFields = mutable.LinkedHashMap.empty[String, ExtendedField]
    val softConflicts = mutable.LinkedHashMap.empty[String, ExtendedField]
    val hardConflicts = mutable.LinkedHashMap.empty[String, Seq[ExtendedField]]

    conflicts.foreach {
      case (k, v) if v.size == 1 =>
        goodFields.put(k, v.head)
      case (k, NonContradictive(v)) =>
        softConflicts.put(k, v)

      case (k, v) =>
        hardConflicts.put(k, v)
    }

    val fc = FieldConflicts(goodFields, softConflicts, hardConflicts)
    // TODO: shitty side effect
    if (fc.hardConflicts.nonEmpty) {
      throw new IDLException(s"Conflicting fields: ${fc.hardConflicts}")
    }
    fc
  }

  private object NonContradictive {
    def unapply(fields: List[ExtendedField]): Option[ExtendedField] = {
      // check that all parent fields have the same type
      if (fields.map(_.field).toSet.size == 1) {
        return Some(fields.head)
      }

      // check covariance - all fields have compatible type
      val x = fields.sortBy(_.defn.distance)
      val sorted = x.map(_.field)
      val primary = sorted.head

      if (sorted.tail.forall(f => isParent(primary.typeId, f.typeId))) {
        return Some(x.head)
      }

      None
    }
  }

  private def isParent(typeId: TypeId, maybeParent: TypeId): Boolean = {
    inheritance.parentsInherited(typeId).contains(maybeParent)
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

  def constructors(struct: Struct): List[ConverterDef] = {
    val local = struct.localOrAmbigious
    val localNamesSet = local.map(_.field.name).toSet

    val cdef = if (struct.all.nonEmpty) {
      val constructorCode = struct.all
        .filterNot(f => localNamesSet.contains(f.field.name))
        .map(f => SigParam(f.field.name, SigParamSource(f.defn.definedBy, tools.idToParaName(f.defn.definedBy)), Some(f.field.name)))

      val constructorCodeNonUnique = local
        .map(f => SigParam(f.field.name, SigParamSource(f.field.typeId, f.field.name), None))

      List(tools.mkConverter(List.empty, constructorCode ++ constructorCodeNonUnique, struct.id))

    } else {
      List.empty
    }


    val mcdef = struct.id match {
      case dto: DTOId if !types.isInterfaceEphemeral(dto) =>
        val mirrorId = tools.defnId(dto)

        val source = SigParamSource(mirrorId, tools.idToParaName(mirrorId))
        val constructorCode = struct.all
          .map(f => SigParam(f.field.name, source, Some(f.field.name)))

        List(ConverterDef(
          struct.id
          , constructorCode
          , List(source)
        ))
      case _ =>
        List.empty
    }

    cdef ++ mcdef
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

          val localNames = localFields.map(_.name)

          val filteredParentFields = parentInstanceFields.filterNot(f => localNames.contains(f.name))

          val mixinInstanceFieldsCandidates = istruct
            .unambigiousInherited
            .map(_.defn.definedBy)
            .collect({ case i: InterfaceId => i })
            .flatMap(mi => structure(resolver.get(mi)).all)
            .filter(f => all.contains(f.field)) // to drop removed fields
            .filterNot(f => parentInstanceFields.contains(f.field))
            .filterNot(f => localFields.contains(f.field))

          val resolvedMixinInstanceFields = findConflicts(mixinInstanceFieldsCandidates)
          val mixinInstanceFields = resolvedMixinInstanceFields.all

          val allFieldCandidates = mixinInstanceFields.map(_.field) ++ filteredParentFields.toList ++ localFields.toList

          assert(allFieldCandidates.groupBy(_.name).forall(_._2.size == 1),
            s"""IDL Compiler bug: contradictive converters for $id:
               |Filtered parents:
               |${filteredParentFields.mkString("\n  ")}
               |
               |Mixins:
               |${mixinInstanceFields.mkString("\n  ")}
               |
               |Local:
               |${localFields.mkString("\n  ")}
               |""".stripMargin)

          val instanceFields = filteredParentFields.toList
          val childMixinFields = mixinInstanceFields
          val innerFields = instanceFields.map(f => SigParam(f.name, SigParamSource(id, "_value"), Some(f.name)))

          val outerFields = localFields.toList.map(f => SigParam(f.name, SigParamSource(f.typeId, f.name), None)) ++
            childMixinFields.map(f => SigParam(f.field.name, SigParamSource(f.defn.definedBy, tools.idToParaName(f.defn.definedBy)), Some(f.field.name)))

          val targetId = istruct.id

          tools.mkConverter(innerFields, outerFields, targetId)
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
