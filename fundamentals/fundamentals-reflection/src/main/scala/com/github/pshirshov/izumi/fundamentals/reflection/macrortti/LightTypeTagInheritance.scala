package com.github.pshirshov.izumi.fundamentals.reflection.macrortti

import com.github.pshirshov.izumi.fundamentals.collections.ImmutableMultiMap
import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LightTypeTag._

import scala.collection.mutable

final class LightTypeTagInheritance(self: FLTT, other: FLTT) {
  final val nothing = NameReference("scala.Nothing", None)
  final val any = NameReference("scala.Any", None)
  final val anyRef = NameReference("scala.AnyRef", None)

  lazy val ib: ImmutableMultiMap[NameReference, NameReference] = FLTT.mergeIDBs(self.idb, other.idb)
  lazy val bdb: ImmutableMultiMap[AbstractReference, AbstractReference] = FLTT.mergeIDBs(self.basesdb, other.basesdb)

//  import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
//  println("")
//  println(ib.niceList())
//  println(bdb.niceList())

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
    isChild(st, ot, List.empty)
  }

  private def isChild(st: LightTypeTag, ot: LightTypeTag, ctx: List[LambdaParameter]): Boolean = {

    if (st == ot) {
      true
    } else if (st == nothing || ot == any || ot == anyRef) {
      // TODO: we may want to check that in case of anyref target type is not a primitve (though why?)
      true
    }
    else {
      (st, ot) match {
        case (s: FullReference, o: FullReference) =>
          if (parentsOf(s.asName).contains(o)) {
            true
          } else {
            bdb.get(s) match {
              case Some(parents) =>
                def oneOfKnownParentsIsInherited(o: FullReference, parents: Set[AbstractReference]): Boolean = {
                  // this (definitely safe) policy allows us to support the following cases:
                  // - F2[_] <: F1[_] => F2[Int] <: F1[Int]
                  // - shape-changing parents
                  // though it comes with a price of additional tree overhead/runtime overhead.
                  //println(s"$st vs $ot, $parents")

                  parents.exists {
                    knownParent =>
                      isChild(knownParent, o, ctx)

                  }
                }
                oneOfKnownParentsIsInherited(o, parents) || shapeHeuristic(s, o, ctx)
              case _ =>
                shapeHeuristic(s, o, ctx)
            }
          }
        case (s: FullReference, o: NameReference) =>
          parentsOf(s.asName).contains(o)
        case (s: NameReference, _: FullReference) =>
          parentsOf(s).exists(p => isChild(p, ot, ctx)) || bdb.get(s).toSeq.flatten.exists(p => isChild(p, ot, ctx))
        case (s: NameReference, o: NameReference) =>
          parentsOf(s).exists(p => isChild(p, ot, ctx)) || ctx.map(_.name).contains(o.ref)
        case (_: AppliedReference, o: Lambda) =>
          isChild(st, o.output, o.input)
        case (_: Lambda, _: AppliedReference) =>
          // TODO: this may be useful in case we consider boundaries

          false
        case (s: Lambda, o: Lambda) =>
          s.input == o.input && isChild(s.output, o.output, ctx)
      }
    }
  }

  private def shapeHeuristic(s: FullReference, o: FullReference, ctx: List[LambdaParameter]): Boolean = {
    def parameterShapeCompatible: Boolean = {
      s.parameters.zip(o.parameters).forall {
        case (sp, op) =>
          sp.variance match {
            case Variance.Invariant =>
              sp.ref == op.ref
            case Variance.Contravariant =>
              isChild(op.ref, sp.ref, ctx)
            case Variance.Covariant =>
              isChild(sp.ref, op.ref, ctx)
          }
      }
    }

    def sameArity: Boolean = {
      s.parameters.size == o.parameters.size
    }

    sameArity && isChild(s.asName, o.asName, ctx) && parameterShapeCompatible
  }
}
