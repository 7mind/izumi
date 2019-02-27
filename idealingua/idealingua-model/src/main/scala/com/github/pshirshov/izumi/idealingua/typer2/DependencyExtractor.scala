package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawAdt.Member
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn.TypeDefn
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{RawStructure, RawTopLevelDefn, RawTypeDef}
import com.github.pshirshov.izumi.idealingua.typer2.Typer2.{Identified, OriginatedDefn, UnresolvedName}

class DependencyExtractor(index: DomainIndex) {
  val types: Seq[TypeDefn] = index.types
  val source: DomainId = index.defn.id

  def groupByType(): Seq[Identified] = {
    val identified = types.map {
      case t@RawTopLevelDefn.TLDBaseType(v) =>
        Identified(index.makeAbstract(v.id), dependsOn(v), Seq(OriginatedDefn(source, t)))
      case t@RawTopLevelDefn.TLDNewtype(v) =>
        Identified(index.makeAbstract(v.id.toIndefinite), Set(index.makeAbstract(v.source)), Seq(OriginatedDefn(source, t)))
      case t@RawTopLevelDefn.TLDDeclared(v) =>
        Identified(index.makeAbstract(v.id), Set.empty, Seq(OriginatedDefn(source, t)))
      case t@RawTopLevelDefn.TLDForeignType(v) =>
        Identified(index.makeAbstract(v.id), Set.empty, Seq(OriginatedDefn(source, t)))
    }

    identified
  }

  private def dependsOn(v: RawTypeDef.WithId): Set[UnresolvedName] = {
    v match {
      case t: RawTypeDef.Interface =>
        dependsOn(t.struct)

      case t: RawTypeDef.DTO =>
        dependsOn(t.struct)

      case t: RawTypeDef.Alias =>
        Set(index.makeAbstract(t.target))

      case t: RawTypeDef.Adt =>
        // we need to extract top level references only. Nested ephemerals are not required
        t.alternatives
          .flatMap {
            case a: Member.TypeRef =>
              Set(index.makeAbstract(a.typeId))
            case a: Member.NestedDefn =>
              dependsOn(a.nested)
          }
          .toSet

      case _: RawTypeDef.Enumeration =>
        Set.empty

      case _: RawTypeDef.Identifier =>
        Set.empty
    }
  }

  private def dependsOn(struct: RawStructure): Set[UnresolvedName] = {
    (struct.interfaces.map(index.makeAbstract) ++ struct.concepts.map(index.makeAbstract) ++ struct.removedConcepts.map(index.makeAbstract)).toSet
  }
}
