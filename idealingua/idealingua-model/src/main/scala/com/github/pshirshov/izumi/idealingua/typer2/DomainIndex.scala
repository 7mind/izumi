package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn.TypeDefn
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.domains.{DomainMeshResolved, Import}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.{RawDeclaredTypeName, RawNongenericRef, RawRef, RawTypeNameRef}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model.{IzDomainPath, IzName, IzNamespace, IzPackage}
import com.github.pshirshov.izumi.idealingua.typer2.Typer2.UnresolvedName
import com.github.pshirshov.izumi.idealingua.typer2.model.{IzType, IzTypeId, TypePrefix}

final class DomainIndex(val defn: DomainMeshResolved) {
  lazy val types: Seq[TypeDefn] = defn.members.collect({ case m: TypeDefn => m })
  lazy val services: Seq[RawTopLevelDefn.TLDService] = defn.members.collect({ case m: RawTopLevelDefn.TLDService => m })
  lazy val buzzers: Seq[RawTopLevelDefn.TLDBuzzer] = defn.members.collect({ case m: RawTopLevelDefn.TLDBuzzer => m })
  lazy val streams: Seq[RawTopLevelDefn.TLDStreams] = defn.members.collect({ case m: RawTopLevelDefn.TLDStreams => m })
  lazy val consts: Seq[RawTopLevelDefn.TLDConsts] = defn.members.collect({ case m: RawTopLevelDefn.TLDConsts => m })
  lazy val dependencies: DependencyExtractor = new DependencyExtractor(this)

  Quirks.discard(services, buzzers)
  Quirks.discard(streams, consts)

  lazy val importIndex: Map[String, Import] = {
    val asList = defn.imports.flatMap(i => i.identifiers.map(id => id.importedAs -> i))
    asList.groupBy(_._1).mapValues(v => if (v.size > 1) {
      ???
    } else {
      v.head._2
    })
  }
  lazy val builtinPackage: IzPackage = IzPackage(Seq(IzDomainPath("_builtins_")))

  lazy val builtins: Map[UnresolvedName, IzType.BuiltinType] = Builtins.all.map(b => makeAbstract(b.id) -> b).toMap

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

  def resolveRef(id: RawNongenericRef): IzTypeId = {
    resolveRef(RawTypeNameRef(id.pkg, id.name))
  }

  def resolveRef(id: RawTypeNameRef): IzTypeId = {
    val unresolved = makeAbstract(id)
    val pkg = makePkg(unresolved)
    val name = IzName(unresolved.name)

    toType(pkg, name)
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
