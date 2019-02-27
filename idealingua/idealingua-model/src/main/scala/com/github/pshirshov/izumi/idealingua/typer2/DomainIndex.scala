package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn.TypeDefn
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.domains.{DomainMeshResolved, Import}
import com.github.pshirshov.izumi.idealingua.typer2.Typer2.UnresolvedName

class DomainIndex(val defn: DomainMeshResolved) {
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

  val builtins: Map[UnresolvedName, IzType.BuiltinType] = Builtins.all.map(b => makeAbstract(b.id) -> b).toMap

  def makeAbstract(id: TypeId): UnresolvedName = {
    val typename = id.name
    id.path.domain match {
      case DomainId.Undefined =>
        resolveTLName(typename)

      case DomainId.Builtin =>
        toBuiltinName(typename)

      case did =>
        UnresolvedName(did.toPackage, typename)

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

  def makeAbstract(id: AbstractIndefiniteId): UnresolvedName = {
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

  private def toBuiltinName(name: String) = {
    UnresolvedName(Seq("_builtins_"), name)
  }
}
