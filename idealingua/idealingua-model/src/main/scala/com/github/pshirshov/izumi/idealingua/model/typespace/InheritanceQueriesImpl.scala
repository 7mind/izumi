package com.github.pshirshov.izumi.idealingua.model.typespace

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{Builtin, StructureId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.exceptions.{IDLCyclicInheritanceException, IDLException}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Structures
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.DTO

protected[typespace] class InheritanceQueriesImpl(ts: Typespace) extends InheritanceQueries {
  def allParents(id: TypeId): List[InterfaceId] = {
    safeAllParents(id, Set.empty)
  }

  def implementingDtos(id: InterfaceId): List[DTOId] = {
    ts.types.index.underlying.collect { // 2.13 compat
      case (tid, d: DTO) if parentsInherited(tid).contains(id) =>
        d.id
    }.toList
  }

  def parentsInherited(id: TypeId): List[InterfaceId] = {
    safeParentsInherited(id, Set.empty)
  }

  protected[typespace] def compatibleDtos(id: InterfaceId): List[DTOId] = {
    ts.types.index.underlying.collect { // 2.13 compat
      case (tid, d: DTO) if allParents(tid).contains(id) =>
        d.id
    }.toList
  }

  protected def safeAllParents(id: TypeId, excluded: Set[TypeId]): List[InterfaceId] = {
    safeParentsInherited(id, excluded) ++ safeParentsConcepts(id, excluded)
  }

  protected def safeParentsInherited(id: TypeId, excluded: Set[TypeId]): List[InterfaceId] = {
    id match {
      case i: InterfaceId =>
        val defn = ts.resolver.get(i)
        val parents = defn.struct.superclasses.interfaces
        val newExclusions = checkCycles(excluded, i, parents)
        List(i) ++ parents.flatMap(safeParentsInherited(_, newExclusions))

      case i: DTOId =>
        val defn = ts.resolver.get(i)
        val parents = defn.struct.superclasses.interfaces
        val newExclusions = checkCycles(excluded, i, parents)
        parents.flatMap(safeParentsInherited(_, newExclusions))

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
    }
  }

  protected def safeParentsConcepts(id: TypeId, excluded: Set[TypeId]): List[InterfaceId] = {
    id match {
      case i: StructureId =>
        val defn = ts.resolver.get(i)
        val superclasses = defn.struct.superclasses
        val removed = superclasses.removedConcepts.toSet
        val newExclusions = checkCycles(excluded, i, superclasses.concepts)
        superclasses.concepts.flatMap(safeAllParents(_, newExclusions)).filterNot(removed.contains)

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
    }
  }

  protected def checkCycles(excluded: Set[TypeId], self: StructureId, parents: Structures): Set[TypeId] = {
    val cycles = parents.toSet[TypeId].intersect(excluded)
    if (cycles.nonEmpty) {
      throw new IDLCyclicInheritanceException(s"Cyclic inheritance detected, cycle elements: ${cycles.mkString("[", ",", "]")}", cycles)
    }
    excluded ++ parents.toSet ++ Set(self)
  }
}
