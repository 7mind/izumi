package com.github.pshirshov.izumi.idealingua.typer2.indexing

import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn.TypeDefn
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.domains.{DomainMeshResolved, ImportedId}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.{RawDeclaredTypeName, RawNongenericRef, RawRef, RawTypeNameRef}
import com.github.pshirshov.izumi.idealingua.typer2.Typer2.TypenameRef
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model.{IzDomainPath, IzName, IzNamespace, IzPackage}
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail.ConflictingImports
import com.github.pshirshov.izumi.idealingua.typer2.model._

final case class GoodImport(domain: DomainId, id: ImportedId)

final class DomainIndex private (val defn: DomainMeshResolved) {
  import DomainIndex._

  val types: Seq[TypeDefn] = defn.members.collect({ case m: TypeDefn => m })
  val declaredTypes: Seq[TypeDefn] = defn.members
    .collect({ case m: RawTopLevelDefn.TLDDeclared => m })
    .map(_.v)
    .collect({ case m: TypeDefn => m })
  val consts: Seq[RawTopLevelDefn.TLDConsts] = defn.members.collect({ case m: RawTopLevelDefn.TLDConsts => m })

  val dependencies: DependencyExtractor = new DependencyExtractor(this)

  val importIndex: Map[String, GoodImport] = {
    val asList = defn.imports.flatMap(i => i.identifiers.map(id => id.importedAs -> GoodImport(i.id, id)))
    val grouped = asList.groupBy(_._1)

    val bad = grouped.filter(_._2.size > 1)
    if (bad.nonEmpty) {
      throw Fail(List(ConflictingImports(bad.mapValues(_.map(_._2).toSet))))
    }

    grouped.mapValues(_.head._2)
  }

  val builtinPackage: IzPackage = IzPackage(Seq(IzDomainPath("_builtins_")))

  val builtins: Map[TypenameRef, IzType.BuiltinType] = Builtins.mappingAll.map {
    case (id, b) =>
      makeAbstract(id) -> b
  }

  def makeAbstract(id: RawRef): TypenameRef = {
    makeAbstract(RawTypeNameRef(id.pkg, id.name))
  }

  def makeAbstract(id: RawTypeNameRef): TypenameRef = {
    val typename = id.name
    if (id.pkg.isEmpty) {
      resolveTLName(typename)
    } else {
      TypenameRef(id.pkg, typename)
    }
  }

  def makeAbstract(id: RawNongenericRef): TypenameRef = {
    // generic args are dropped here!
    if (id.pkg.nonEmpty && id.pkg != Seq(".")) {
      TypenameRef(id.pkg, id.name)
    } else {
      resolveTLName(id.name)
    }
  }

  def makeAbstract(id: IzTypeId.BuiltinTypeId): TypenameRef = {
    val name = id.name.name
    toBuiltinName(name)
  }

  def resolveTopLeveleName(id: RawDeclaredTypeName): TypenameRef = {
    resolveTLName(id.name)
  }

  def resolveRef(id: RawTypeNameRef): IzTypeId = {
    val unresolved = makeAbstract(id)
    toId(Seq.empty, unresolved)
  }

  def toId(subNamespace: Seq[IzNamespace], unresolvedName: TypenameRef): IzTypeId = {
    val pkg = makePkg(unresolvedName)
    val name = IzName(unresolvedName.name)
    if (subNamespace.isEmpty) {
      toType(pkg, name)
    } else {
      IzTypeId.UserTypeId(TypePrefix.UserT(pkg, subNamespace), name)
    }
  }

  private def resolveTLName(typename: TypeName): TypenameRef = {
    importIndex.get(typename) match {
      case Some(value) =>
        if (value.id.importedAs != value.id.name) {
          TypenameRef(defn.id.toPackage, typename)
        } else {
          TypenameRef(value.domain.toPackage, typename)
        }
      case None =>
        builtins.get(toBuiltinName(typename)) match {
          case Some(v) =>
            toBuiltinName(v.id.name.name)
          case None => // not imported and not builtin => this domain
            TypenameRef(defn.id.toPackage, typename)
        }

    }
  }

  private def makePkg(unresolved: TypenameRef): IzPackage = {
    IzPackage(unresolved.pkg.map(IzDomainPath))
  }

  private def toType(pkg: IzPackage, name: IzName): IzTypeId = {
    if (pkg == builtinPackage) {
      IzTypeId.BuiltinTypeId(name)
    } else {
      IzTypeId.UserTypeId(TypePrefix.UserTLT(pkg), name)
    }
  }

  private def toBuiltinName(name: String): TypenameRef = {
    TypenameRef(builtinPackage.path.map(_.name), name)
  }
}

object DomainIndex {
  case class Fail(failures: List[T2Fail]) extends RuntimeException
  def build(defn: DomainMeshResolved): Either[List[T2Fail], DomainIndex] = {
    try {
      Right(new DomainIndex(defn))
    } catch {
      case f: Fail =>
        Left(f.failures)
    }

  }
}
