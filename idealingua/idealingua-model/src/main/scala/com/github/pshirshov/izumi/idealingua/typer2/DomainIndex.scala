package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn.TypeDefn
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.domains.{DomainMeshResolved, Import}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.{RawDeclaredTypeName, RawNongenericRef, RawRef, RawTypeNameRef}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model.{IzDomainPath, IzName, IzNamespace, IzPackage}
import com.github.pshirshov.izumi.idealingua.typer2.Typer2.UnresolvedName
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail.ConflictingImports
import com.github.pshirshov.izumi.idealingua.typer2.model.{IzType, IzTypeId, T2Fail, TypePrefix}

final class DomainIndex private (val defn: DomainMeshResolved) {
  import DomainIndex._

  val types: Seq[TypeDefn] = defn.members.collect({ case m: TypeDefn => m })
  val services: Seq[RawTopLevelDefn.TLDService] = defn.members.collect({ case m: RawTopLevelDefn.TLDService => m })
  val buzzers: Seq[RawTopLevelDefn.TLDBuzzer] = defn.members.collect({ case m: RawTopLevelDefn.TLDBuzzer => m })
  val streams: Seq[RawTopLevelDefn.TLDStreams] = defn.members.collect({ case m: RawTopLevelDefn.TLDStreams => m })
  val consts: Seq[RawTopLevelDefn.TLDConsts] = defn.members.collect({ case m: RawTopLevelDefn.TLDConsts => m })

  val decls: Seq[RawTopLevelDefn.NamedDefn] = defn.members.collect({ case m: RawTopLevelDefn.TLDDeclared => m }).map(_.v)
  val declaredTypes: Seq[TypeDefn] = decls.collect({ case m: TypeDefn => m })
  val declaredServices: Seq[RawTopLevelDefn.TLDService] = decls.collect({ case m: RawTopLevelDefn.TLDService => m })
  val declaredBuzzers: Seq[RawTopLevelDefn.TLDBuzzer] = decls.collect({ case m: RawTopLevelDefn.TLDBuzzer => m })
  val declaredStreams: Seq[RawTopLevelDefn.TLDStreams] = decls.collect({ case m: RawTopLevelDefn.TLDStreams => m })


  val dependencies: DependencyExtractor = new DependencyExtractor(this)

  Quirks.discard(services, buzzers)
  Quirks.discard(streams, consts)

  val importIndex: Map[String, Import] = {
    val asList = defn.imports.flatMap(i => i.identifiers.map(id => id.importedAs -> i))
    val grouped = asList.groupBy(_._1)

    val bad = grouped.filter(_._2.size > 1)
    if (bad.nonEmpty) {
      throw Fail(List(ConflictingImports(bad.mapValues(_.map(_._2).toSet))))
    }

    grouped.mapValues(_.head._2)
  }
  val builtinPackage: IzPackage = IzPackage(Seq(IzDomainPath("_builtins_")))

  val builtins: Map[UnresolvedName, IzType.BuiltinType] = Builtins.all.map(b => makeAbstract(b.id) -> b).toMap

  def makeAbstract(id: RawRef): UnresolvedName = {
    makeAbstract(RawTypeNameRef(id.pkg, id.name))
  }

  def makeAbstract(id: RawTypeNameRef): UnresolvedName = {
    val typename = id.name
    if (id.pkg.isEmpty) {
      resolveTLName(typename)
    } else {
      UnresolvedName(id.pkg, typename)
    }
  }

  def makeAbstract(id: RawNongenericRef): UnresolvedName = {
    // generic args are dropped here!
    if (id.pkg.nonEmpty && id.pkg != Seq(".")) {
      UnresolvedName(id.pkg, id.name)
    } else {
      resolveTLName(id.name)
    }
  }

  def makeAbstract(id: IzTypeId.BuiltinType): UnresolvedName = {
    val name = id.name.name
    toBuiltinName(name)
  }

  def resolveTopLeveleName(id: RawDeclaredTypeName): UnresolvedName = {
    resolveTLName(id.name)
  }

  def resolveRef(id: RawTypeNameRef): IzTypeId = {
    val unresolved = makeAbstract(id)
    val pkg = makePkg(unresolved)
    toId(Seq.empty, unresolved)
  }

  def toId(subNamespace: Seq[IzNamespace], unresolvedName: UnresolvedName): IzTypeId = {
    val pkg = makePkg(unresolvedName)
    val name = IzName(unresolvedName.name)
    if (subNamespace.isEmpty) {
      toType(pkg, name)
    } else {
      IzTypeId.UserType(TypePrefix.UserT(pkg, subNamespace), name)
    }
  }

  private def resolveTLName(typename: TypeName): UnresolvedName = {
    importIndex.get(typename) match {
      case Some(value) =>
        UnresolvedName(value.id.toPackage, typename)
      case None =>

        builtins.get(toBuiltinName(typename)) match {
          case Some(value) =>
            makeAbstract(value.id)
          case None => // not imported and not builtin => this domain
            UnresolvedName(defn.id.toPackage, typename)
        }

    }
  }

  private def makePkg(unresolved: UnresolvedName): IzPackage = {
    IzPackage(unresolved.pkg.map(IzDomainPath))
  }

  private def toType(pkg: IzPackage, name: IzName): IzTypeId = {
    if (pkg == builtinPackage) {
      IzTypeId.BuiltinType(name)
    } else {
      IzTypeId.UserType(TypePrefix.UserTLT(pkg), name)
    }
  }

  private def toBuiltinName(name: String): UnresolvedName = {
    UnresolvedName(builtinPackage.path.map(_.name), name)
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
