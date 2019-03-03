package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawAdt.Member
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn.TypeDefn
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{RawStructure, RawTopLevelDefn, RawTypeDef}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.RawDeclaredTypeName
import com.github.pshirshov.izumi.idealingua.typer2.Typer2.{Operation, OriginatedDefn, UnresolvedName}

class DependencyExtractor(index: DomainIndex) {
  val types: Seq[TypeDefn] = index.types
  val source: DomainId = index.defn.id

  def groupByType(): Seq[Operation] = {
    val identified = types.map {
      case t@RawTopLevelDefn.TLDBaseType(v) =>
        Operation(index.resolveTopLeveleName(v.id), dependsOn(v), Seq(OriginatedDefn(source, t)))
      case t@RawTopLevelDefn.TLDDeclared(v) =>
        Operation(index.resolveTopLeveleName(v.id), dependsOn(v), Seq(OriginatedDefn(source, t)))
      case t@RawTopLevelDefn.TLDNewtype(v) =>
        Operation(index.resolveTopLeveleName(v.id), dependsOn(v), Seq(OriginatedDefn(source, t)))
      case t@RawTopLevelDefn.TLDForeignType(v) =>
        Operation(index.resolveTopLeveleName(RawDeclaredTypeName(v.id.name)), dependsOn(v), Seq(OriginatedDefn(source, t)))
    }

    identified
  }

  private def dependsOn(v: RawTypeDef): Set[UnresolvedName] = {
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

      case n: RawTypeDef.NewType =>
        Set(index.makeAbstract(n.source))

      case _: RawTypeDef.Enumeration =>
        Set.empty

      case _: RawTypeDef.Identifier =>
        Set.empty

      case _: RawTypeDef.DeclaredType =>
        Set.empty

      case _: RawTypeDef.ForeignType =>
        Set.empty
    }
  }

  private def dependsOn(struct: RawStructure): Set[UnresolvedName] = {
    (struct.interfaces.map(index.makeAbstract) ++ struct.concepts.map(index.makeAbstract) ++ struct.removedConcepts.map(index.makeAbstract)).toSet
  }
}
