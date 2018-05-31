package com.github.pshirshov.izumi.idealingua.model.typespace

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{Builtin, StructureId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.DTO

protected[typespace] class InheritanceQueriesImpl(ts: TypeResolver, types: TypeCollection) extends InheritanceQueries {
  def allParents(id: TypeId): List[InterfaceId] = {
    parentsInherited(id) ++ parentsConcepts(id)
  }

  def implementingDtos(id: InterfaceId): List[DTOId] = {
    types.index.collect {
      case (tid, d: DTO) if parentsInherited(tid).contains(id) =>
        d.id
    }.toList
  }

  def parentsInherited(id: TypeId): List[InterfaceId] = {
    id match {
      case i: InterfaceId =>
        val defn = ts.get(i)
        List(i) ++ defn.struct.superclasses.interfaces.flatMap(parentsInherited)

      case i: DTOId =>
        ts.get(i).struct.superclasses.interfaces.flatMap(parentsInherited)

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

  protected[typespace] def compatibleDtos(id: InterfaceId): List[DTOId] = {
    types.index.collect {
      case (tid, d: DTO) if allParents(tid).contains(id) =>
        d.id
    }.toList
  }

  private def parentsConcepts(id: TypeId): List[InterfaceId] = {
    id match {
      case i: StructureId =>
        val superclasses = ts.get(i).struct.superclasses
        val removed = superclasses.removedConcepts.toSet
        superclasses.concepts.flatMap(allParents).filterNot(removed.contains)

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
}
