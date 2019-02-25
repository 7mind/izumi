package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.fundamentals.graphs
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.idealingua.model.common.{AbstractIndefiniteId, DomainId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawAdt.Member
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn.TypeDefn
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{RawStructure, RawTopLevelDefn, RawTypeDef}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.domains.DomainMeshResolved

import scala.collection.mutable


class Typer2(defn: DomainMeshResolved) {

  import Typer2._

  def run(): Unit = {
    //println(s">>> ${defn.id}")
    val r = run1()
    //println()
  }

  def run1(): Either[T2Fail, Unit] = {
    val types = defn.members.collect({ case m: TypeDefn => m })
    val services = defn.members.collect({ case m: RawTopLevelDefn.TLDService => m })
    val buzzers = defn.members.collect({ case m: RawTopLevelDefn.TLDBuzzer => m })
    val streams = defn.members.collect({ case m: RawTopLevelDefn.TLDStreams => m })
    val consts = defn.members.collect({ case m: RawTopLevelDefn.TLDConsts => m })

    Quirks.discard(services, buzzers)
    Quirks.discard(streams, consts)


    val grouped = groupByType(types, defn.id)

    val deps = grouped.mapValues(_.depends)
    val builtins = Builtins.all.map(b => makeAbstract(b.id) -> Set.empty[UnresolvedName]).toMap
    val allDeps = deps ++ builtins


    val circulars = new mutable.ArrayBuffer[Set[UnresolvedName]]()
    val ordered = graphs.toposort.cycleBreaking(allDeps, Seq.empty, (circular: Set[UnresolvedName]) => {
      circulars.append(circular)
      circular.head
    })


    //    println(s"Circulars: ${circulars.niceList()}")
    //    println(s"Ordered: ${ordered.niceList()}")
    Right(())
  }

  private def groupByType(types: Seq[TypeDefn], source: DomainId): Map[UnresolvedName, Identified] = {
    val identified = types.map {
      case t@RawTopLevelDefn.TLDBaseType(v) =>
        Identified(makeAbstract(v.id), dependsOn(v), source, Seq(t))
      case t@RawTopLevelDefn.TLDNewtype(v) =>
        Identified(makeAbstract(v.id.toIndefinite), Set(makeAbstract(v.source)), source, Seq(t))
      case t@RawTopLevelDefn.TLDDeclared(v) =>
        Identified(makeAbstract(v.id), Set(makeAbstract(v.id)), source, Seq(t))
      case t@RawTopLevelDefn.TLDForeignType(v) =>
        Identified(makeAbstract(v.id), Set.empty, source, Seq(t))
    }

    identified.groupBy(_.id).mapValues {
      ops =>
        ops.tail.foldLeft(ops.head) {
          case (acc, op) =>
            assert(acc.id == op.id)
            acc.copy(depends = acc.depends ++ op.depends, defns = acc.defns ++ op.defns)
        }
    }
  }


  private def dependsOn(v: RawTypeDef.WithId): Set[UnresolvedName] = {
    v match {
      case t: RawTypeDef.Interface =>
        dependsOn(t.struct)

      case t: RawTypeDef.DTO =>
        dependsOn(t.struct)

      case t: RawTypeDef.Alias =>
        Set(makeAbstract(t.target))

      case t: RawTypeDef.Adt =>
        t.alternatives
          .map {
            case a: Member.TypeRef =>
              makeAbstract(a.typeId)
            case a: Member.NestedDefn =>
              makeAbstract(a.nested.id)
          }
          .toSet

      case t: RawTypeDef.Enumeration =>
        Set.empty

      case t: RawTypeDef.Identifier =>
        Set.empty
    }
  }

  private def dependsOn(struct: RawStructure): Set[UnresolvedName] = {
    (struct.interfaces.map(makeAbstract) ++ struct.concepts.map(makeAbstract) ++ struct.removedConcepts.map(makeAbstract)).toSet
  }

  //
  private def makeAbstract(id: TypeId): UnresolvedName = {
    val domainPart = id.path.domain match {
      case DomainId.Undefined =>
        Seq.empty
      case DomainId.Builtin =>
        Seq.empty
      case did =>
        did.toPackage
    }
    UnresolvedName(domainPart, id.path.within, id.name)
  }

  private def makeAbstract(id: AbstractIndefiniteId): UnresolvedName = {
    UnresolvedName(id.pkg.filterNot(_.isEmpty), Seq.empty, id.name)
  }

  private def makeAbstract(id: IzTypeId.BuiltinType): UnresolvedName = {
    UnresolvedName(Seq.empty, Seq.empty, id.name.name)
  }
}

object Typer2 {

  sealed trait T2Fail

  case class UnresolvedName(pkg: Seq[String], namespace: Seq[String], name: String) {
    override def toString: String = s"<${pkg.mkString(".")}>.<${namespace.mkString(".")}>.$name"
  }

  case class Identified(id: UnresolvedName, depends: Set[UnresolvedName], source: DomainId, defns: Seq[TypeDefn])

}
