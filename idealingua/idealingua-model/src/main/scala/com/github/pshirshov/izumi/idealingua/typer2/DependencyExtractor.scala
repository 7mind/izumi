package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawAdt.Member
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn.TypeDefn
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{RawStructure, RawTopLevelDefn, RawTypeDef}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.RawDeclaredTypeName
import com.github.pshirshov.izumi.idealingua.typer2.Typer2._

class DependencyExtractor(index: DomainIndex) {
  val types: Seq[TypeDefn] = index.types
  val source: DomainId = index.defn.id

  def groupByType(): Seq[UniqueOperation] = {
    val identified = types.map {
      case t@RawTopLevelDefn.TLDBaseType(v) =>
        DefineType(index.resolveTopLeveleName(v.id), dependsOn(v), OriginatedDefn(source, t))
      case t@RawTopLevelDefn.TLDNewtype(v) =>
        DefineType(index.resolveTopLeveleName(v.id), dependsOn(v), OriginatedDefn(source, t))
      case t@RawTopLevelDefn.TLDTemplate(v) =>
        DefineType(index.resolveTopLeveleName(v.decl.id), dependsOn(v), OriginatedDefn(source, t))
      case t@RawTopLevelDefn.TLDInstance(v) =>
        DefineType(index.resolveTopLeveleName(v.id), dependsOn(v), OriginatedDefn(source, t))
      case t@RawTopLevelDefn.TLDForeignType(v) =>
        DefineType(index.resolveTopLeveleName(RawDeclaredTypeName(v.id.name)), dependsOn(v), OriginatedDefn(source, t))
    }

    identified
  }

  private def dependsOn(v: RawTypeDef): Set[TypenameRef] = {
    v match {
      case t: RawTypeDef.Interface =>
        refs(t.struct)

      case t: RawTypeDef.DTO =>
        refs(t.struct)

      case t: RawTypeDef.Alias =>
        Set(index.makeAbstract(t.target))

      case t: RawTypeDef.Adt =>
        // we need to extract top level references only. Nested ephemerals are not required
        t.contract.map(refs).getOrElse(Set.empty) ++ t.alternatives
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

      case _: RawTypeDef.ForeignType =>
        Set.empty

      case t: RawTypeDef.Template =>
        dependsOn(t.decl)

      case i: RawTypeDef.Instance =>
        Set(index.makeAbstract(i.source))
    }
  }

  private def refs(struct: RawStructure): Set[TypenameRef] = {
    (struct.interfaces ++ struct.concepts ++ struct.removedConcepts).map(index.makeAbstract).toSet
  }
}
