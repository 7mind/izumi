package com.github.pshirshov.izumi.idealingua.model.typespace

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{DTOId, IdentifierId, InterfaceId}
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._
import com.github.pshirshov.izumi.idealingua.model.typespace.structures.{ConverterDef, FieldConflicts, PlainStruct, Struct}

import scala.collection.mutable

protected[typespace] class StructuralQueriesImpl(ts: Typespace) extends StructuralQueries {
  def structure(defn: IdentifierId): PlainStruct = {
    structure(ts.resolver.get(defn))
  }

  def structure(defn: Identifier): PlainStruct = {
    val extractor = new FieldExtractor(ts.resolver, defn.id)
    PlainStruct(extractor.extractFields(defn))
  }

  def structure(id: StructureId): Struct = {
    structure(ts.resolver.get(id))
  }

  def structure(defn: WithStructure): Struct = {
    val extractor = new FieldExtractor(ts.resolver, defn.id)

    val parts = ts.resolver.get(defn.id) match {
      case i: Interface =>
        i.struct.superclasses
      case i: DTO =>
        i.struct.superclasses
    }


    val all = extractor.extractFields(defn)

    val conflicts = findConflicts(all)

    val sortedFields = conflicts.all.sortBy(f => (f.defn.distance, f.defn.definedBy.toString, -f.defn.definedWithIndex)).reverse
    val output = new Struct(defn.id, parts, conflicts.good, conflicts.soft, sortedFields)
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
        val bestField = x.head
        return Some(bestField.copy(defn = bestField.defn.copy(variance = sorted)))
      }

      None
    }
  }

  private def isParent(typeId: TypeId, maybeParent: TypeId): Boolean = {
    ts.inheritance.parentsInherited(typeId).contains(maybeParent)
  }

  def conversions(id: InterfaceId): List[ConverterDef] = {
    val implementors = ts.inheritance.compatibleDtos(id)
    converters(id, implementors)
  }


  override def structuralParents(interface: Struct): List[Struct] = {
    val thisStructure = structure(interface.id)
    val allStructuralParents = List(interface.id) ++ ts.inheritance.allParents(interface.id)

    allStructuralParents
      .distinct
      .map(structure)
      .filter(_.all.map(_.field).diff(thisStructure.all.map(_.field)).isEmpty)
  }

  def sameSignature(tid: StructureId): List[DTO] = {
    val sig = signature(ts.resolver.get(tid))

    ts.types
      .structures
      .filterNot(_.id == tid)
      .filter(another => sig == signature(another))
      .filterNot(_.id == tid)
      .distinct
      .filterNot(id => ts.inheritance.parentsInherited(id.id).contains(tid))
      .collect({ case t: DTO => t })
      .toList
  }

  def constructors(struct: Struct): List[ConverterDef] = {
    val local = struct.localOrAmbigious
    val localNamesSet = local.map(_.field.name).toSet

    val cdef = if (struct.all.nonEmpty) {
      val constructorCode = struct.all
        .filterNot(f => localNamesSet.contains(f.field.name))
        .map(f => SigParam(f.field.name, SigParamSource(f.defn.definedBy, ts.tools.idToParaName(f.defn.definedBy)), Some(f.field.name)))

      val constructorCodeNonUnique = local
        .map(f => SigParam(f.field.name, SigParamSource(f.field.typeId, f.field.name), None))

      List(mkConverter(List.empty, constructorCode ++ constructorCodeNonUnique, struct.id))

    } else {
      List.empty
    }


    val mcdef = struct.id match {
      case dto: DTOId if !ts.types.isInterfaceEphemeral(dto) =>
        val mirrorId = ts.tools.defnId(dto)

        val source = SigParamSource(mirrorId, ts.tools.idToParaName(mirrorId))
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

  def adtBranchScopes(adt: Adt): Map[TypeId, Set[TypeId]] = {
    adt.alternatives
      .filterNot(_.typeId.isInstanceOf[Builtin])
      .map(v => ts.apply(v.typeId))
      .map {
        case a: Adt => (a.id, adtRecursiveMembers(a).toSet)
        case o => (o.id, Set.empty[TypeId])
      }
      .toMap
  }

  def adtRecursiveMembers(adt: Adt): List[TypeId] = {
    adtRecursiveMembers(adt)
  }

  protected def adtRecursiveMembers(adt: Adt, visited: mutable.HashSet[TypeId]): List[TypeId] = {
    val sub = adt.alternatives
      .filterNot(_.typeId.isInstanceOf[Builtin])
      .filterNot(a => visited.contains(a.typeId))
      .map(v => ts.apply(v.typeId))
      .collect {
        case a: Adt =>
          adtRecursiveMembers(a, visited)
      }

    adt.alternatives.map(_.typeId) ++ sub.flatten
  }

  protected def converters(id: InterfaceId, implementors: List[StructureId]): List[ConverterDef] = {
    val struct = structure(ts.resolver.get(id))
    val parentInstanceFields = struct.unambigious.map(_.field).toSet

    implementors
      .map(t => structure(ts.resolver.get(t)))
      .map {
        istruct =>
          val targetId = istruct.id

          val localFields = istruct.localOrAmbigious
            .map(_.field)
            .toSet
          val all = istruct.all.map(_.field).toSet

          val localNames = localFields.map(_.name)

          val filteredParentFields = parentInstanceFields
            .filterNot(f => localNames.contains(f.name))
            .intersect(all)

          val mixinInstanceFieldsCandidates = istruct
            .unambigiousInherited
            .map(_.defn.definedBy)
            .collect({ case i: StructureId => i })
            .flatMap(mi => structure(ts.resolver.get(mi)).all)
            .filter(f => all.contains(f.field)) // to drop removed fields
            .filterNot(f => parentInstanceFields.contains(f.field))
            .filterNot(f => localFields.contains(f.field))

          val resolvedMixinInstanceFields = findConflicts(mixinInstanceFieldsCandidates)
          val mixinInstanceFields = resolvedMixinInstanceFields.all

          val allFieldCandidates = mixinInstanceFields.map(_.field) ++ filteredParentFields.toList ++ localFields.toList

          assert(
            allFieldCandidates.groupBy(_.name).forall(_._2.size == 1),
            s"""IDL Compiler bug: contradictive converter for $id -> $targetId:
               |All fields: ${all.niceList()}
               |Parent fields: ${parentInstanceFields.niceList()}
               |Filtered PFs: ${filteredParentFields.niceList()}
               |Mixins CFs: ${mixinInstanceFields.niceList()}
               |Local Fields: ${localFields.niceList()}
               |""".stripMargin
          )

          val innerFields = filteredParentFields.map(f => SigParam(f.name, SigParamSource(id, "_value"), Some(f.name)))

          val outerFields = localFields.toList.map(f => SigParam(f.name, SigParamSource(f.typeId, f.name), None)) ++
            mixinInstanceFields.map(f => SigParam(f.field.name, SigParamSource(f.defn.definedBy, ts.tools.idToParaName(f.defn.definedBy)), Some(f.field.name)))


          val fieldsToAssign = (innerFields.map(_.targetFieldName) ++ outerFields.map(_.targetFieldName)).toSet
          assert(
            all.map(_.name) == fieldsToAssign,
            s"""IDL Compiler bug: failed to build converter for $id -> $targetId:
               |All fields: ${all.niceList()}
               |Instance fields (inner): ${filteredParentFields.niceList()}
               |Local Fields (outer 1): ${localFields.niceList()}
               |Child Mixin  (outer 2): ${mixinInstanceFields.niceList()}
               |Parent fields: ${parentInstanceFields.niceList()}
               |Mixins CFs: ${mixinInstanceFields.niceList()}
               |""".stripMargin
          )

          mkConverter(innerFields.toList, outerFields, targetId)
      }
  }


  protected def signature(defn: WithStructure): List[Field] = {
    structure(defn).all.map(_.field).sortBy(_.name)
  }

  protected def mkConverter(innerFields: List[SigParam], outerFields: List[SigParam], targetId: StructureId): ConverterDef = {
    val allFields = innerFields ++ outerFields
    val outerParams = outerFields.map(_.source).distinct
    assert(innerFields.groupBy(_.targetFieldName).forall(_._2.size == 1), s"$targetId: Contradictive inner fields: ${innerFields.niceList()}")
    assert(outerFields.groupBy(_.targetFieldName).forall(_._2.size == 1), s"$targetId: Contradictive outer fields: ${outerFields.niceList()}")
    assert(allFields.groupBy(_.targetFieldName).forall(_._2.size == 1), s"$targetId: Contradictive fields: ${allFields.niceList()}")
    assert(outerParams.groupBy(_.sourceName).forall(_._2.size == 1), s"$targetId: Contradictive outer params: ${outerParams.niceList()}")

    // TODO: pass definition instead of id
    ConverterDef(
      targetId
      , allFields
      , outerParams
    )
  }
}
