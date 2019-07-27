package com.github.pshirshov.izumi.fundamentals.reflection.macrortti

import com.github.pshirshov.izumi.fundamentals.collections.ImmutableMultiMap
import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LightTypeTag._

import scala.collection.mutable

final class LightTypeTagInheritance(self: FLTT, other: FLTT) {
  final val nothing = NameReference("scala.Nothing")
  final val any = NameReference("scala.Any")
  final val anyRef = NameReference("scala.AnyRef")

  lazy val ib: ImmutableMultiMap[NameReference, NameReference] = FLTT.mergeIDBs(self.idb, other.idb)
  lazy val bdb: ImmutableMultiMap[AbstractReference, AbstractReference] = FLTT.mergeIDBs(self.basesdb, other.basesdb)



  def parentsOf(t: NameReference): Set[AppliedReference] = {
    val out = mutable.HashSet[NameReference]()
    val tested = mutable.HashSet[NameReference]()
    parentsOf(t, out, tested)
    out.toSet
  }

  def parentsOf(t: NameReference, out: mutable.HashSet[NameReference], tested: mutable.HashSet[NameReference]): Unit = {
    val direct = ib.get(t).toSet.flatten
    tested += t
    out ++= direct

    val nextNames = direct.map(_.asName)
    nextNames
      .filterNot(tested.contains)
      .foreach {
        b =>
          parentsOf(b.asName, out, tested)
      }

  }


  def isChild(): Boolean = {
    val st = self.t
    val ot = other.t
    isChild(st, ot)
  }

  private def isChild(st: LightTypeTag, ot: LightTypeTag): Boolean = {
    if (st == ot) {
      true
    } else if (st == nothing || ot == any || (ot == anyRef && true)) {
      // TODO: check anyref also
      true
    }
    else {
      (st, ot) match {
        case (s: FullReference, o: FullReference) =>
          // TODO:
          s == o
          if (parentsOf(s.asName).contains(o)) {
            true
          } else {
            bdb.get(s) match {
              case Some(value) =>
                if (value.contains(o)) {
                  println("STRONG-MATCH")
                  true
                } else {
                  shapeHeuristic(s, o)
                }

              case None =>
                shapeHeuristic(s, o)
            }
          }
        case (s: FullReference, o: NameReference) =>
          parentsOf(s.asName).contains(o)
        case (s: NameReference, o: FullReference) =>
          parentsOf(s).contains(o)
        case (s: NameReference, o: NameReference) =>
          parentsOf(s).contains(o)
        case (s: AppliedReference, o: Lambda) =>
          false
        case (s: Lambda, o: AppliedReference) =>
          false
        case (s: Lambda, o: Lambda) =>
          // TODO:
          s.input == o.input && isChild(s.output, o.output)
      }
    }
  }

  private def shapeHeuristic(s: FullReference, o: FullReference): Boolean = {
    s.parameters.size == o.parameters.size && isChild(s.asName, o.asName) && s.parameters.zip(o.parameters).forall {
      case (sp, op) =>
        isChild(sp.ref, op.ref)
    }
  }
}
