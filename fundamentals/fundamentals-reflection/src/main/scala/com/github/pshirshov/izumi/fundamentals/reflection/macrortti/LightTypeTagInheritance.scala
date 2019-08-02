package com.github.pshirshov.izumi.fundamentals.reflection.macrortti

import com.github.pshirshov.izumi.fundamentals.collections.ImmutableMultiMap
import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LightTypeTag._
import com.github.pshirshov.izumi.fundamentals.platform.basics.IzBoolean._

import scala.collection.mutable

final class LightTypeTagInheritance(self: FLTT, other: FLTT) {
  final val tpeNothing = NameReference("scala.Nothing")
  final val tpeAny = NameReference("scala.Any")
  final val tpeAnyRef = NameReference("scala.AnyRef")
  final val tpeObject = NameReference("java.lang.Object")

  lazy val ib: ImmutableMultiMap[NameReference, NameReference] = FLTT.mergeIDBs(self.idb, other.idb)
  lazy val bdb: ImmutableMultiMap[AbstractReference, AbstractReference] = FLTT.mergeIDBs(self.basesdb, other.basesdb)


  def isChild(): Boolean = {
    val st = self.t
    val ot = other.t
    isChild(st, ot, List.empty)
  }

  private def debug() = {
    import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

    println(s"${self} vs ${other}")
    println(s"inheritance: ${ib.niceList()}")
    println(s"bases: ${bdb.niceList()}")
  }

  private def isChild(selfT: LightTypeTag, thatT: LightTypeTag, ctx: List[LambdaParameter]): Boolean = {
    (selfT, thatT) match {
      case (s, t) if s == t =>
        true
      case (s, _) if s == tpeNothing =>
        true
      case (_, t) if t == tpeAny || t == tpeAnyRef || t == tpeObject =>
        // TODO: we may want to check that in case of anyref target type is not a primitve (though why?)
        true
      case (s: FullReference, t: FullReference) =>
        if (parentsOf(s.asName).contains(t)) {
          true
        } else {
          oneOfKnownParentsIsInheritedFrom(ctx, s, t) || shapeHeuristic(s, t, ctx)
        }
      case (s: FullReference, t: NameReference) =>
        oneOfKnownParentsIsInheritedFrom(ctx, s, t)
      case (s: NameReference, t: FullReference) =>
        oneOfKnownParentsIsInheritedFrom(ctx, s, t)
      case (s: NameReference, t: NameReference) =>
        val boundIsOk = t.boundaries match {
          case Boundaries.Defined(bottom, top) =>
            isChild(s, top, ctx) && isChild(bottom, s, ctx)
          case Boundaries.Empty =>
            true
        }

        any(
          all(boundIsOk, parentsOf(s).exists(p => isChild(p, thatT, ctx))),
          all(boundIsOk, ctx.map(_.name).contains(t.ref)), // lambda parameter may accept anything
          s.boundaries match {
            case Boundaries.Defined(_, top) =>
              isChild(top, t, ctx)
            case Boundaries.Empty =>
              false
          }
        )


      case (_: AppliedNamedReference, t: Lambda) =>
        isChild(selfT, t.output, t.input)
      case (s: Lambda, t: AppliedNamedReference) =>
        isChild(s.output, t, s.input)
      case (s: Lambda, o: Lambda) =>
        s.input == o.input && isChild(s.output, o.output, s.input)
      case (s: IntersectionReference, t: IntersectionReference) =>
        // yeah, this shit is quadratic
        s.refs.forall {
          c =>
            t.refs.exists {
              p =>
                isChild(c, p, ctx)
            }
        }
      case (s: IntersectionReference, t: LightTypeTag) =>
        s.refs.exists(c => isChild(c, t, ctx))
      case (s: LightTypeTag, o: IntersectionReference) =>
        o.refs.forall(t => isChild(s, t, ctx))

      case (s: Refinement, t: Refinement) =>
        isChild(s.reference, t.reference, ctx) && t.decls.diff(s.decls).isEmpty
      case (s: Refinement, t: LightTypeTag) =>
        isChild(s.reference, t, ctx)
      case (_: LightTypeTag, _: Refinement) =>
        false
    }
  }

  private def shapeHeuristic(self: FullReference, that: FullReference, ctx: List[LambdaParameter]): Boolean = {
    def parameterShapeCompatible: Boolean = {
      self.parameters.zip(that.parameters).forall {
        case (ps, pt) =>
          ps.variance match {
            case Variance.Invariant =>
              ps.ref == pt.ref
            case Variance.Contravariant =>
              isChild(pt.ref, ps.ref, ctx)
            case Variance.Covariant =>
              isChild(ps.ref, pt.ref, ctx)
          }
      }
    }

    def sameArity: Boolean = {
      self.parameters.size == that.parameters.size
    }

    sameArity && isChild(self.asName, that.asName, ctx) && parameterShapeCompatible
  }

  private def parentsOf(t: NameReference): Set[AppliedNamedReference] = {
    val out = mutable.HashSet[NameReference]()
    val tested = mutable.HashSet[NameReference]()
    parentsOf(t, out, tested)
    out.toSet
  }


  private def parentsOf(t: NameReference, out: mutable.HashSet[NameReference], tested: mutable.HashSet[NameReference]): Unit = {
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

  private def safeParentsOf(t: AbstractReference): Seq[AbstractReference] = {
    bdb.get(t).toSeq.flatten
  }

  private def oneOfKnownParentsIsInheritedFrom(ctx: List[LambdaParameter], child: AbstractReference, parent: AbstractReference) = {
    safeParentsOf(child).exists(p => isChild(p, parent, ctx))
  }

}
