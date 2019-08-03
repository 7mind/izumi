package com.github.pshirshov.izumi.fundamentals.reflection.macrortti

import com.github.pshirshov.izumi.fundamentals.collections.ImmutableMultiMap
import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LightTypeTag._
import com.github.pshirshov.izumi.fundamentals.platform.basics.IzBoolean._
import com.github.pshirshov.izumi.fundamentals.platform.console.TrivialLogger
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LightTypeTagInheritance.Ctx

import scala.collection.mutable

object LightTypeTagInheritance {
  case class Ctx(params: List[LambdaParameter], logger: TrivialLogger) {
    def next(): Ctx = Ctx(params, logger.sub())
    def next(newparams: List[LambdaParameter]): Ctx = Ctx(newparams, logger.sub())
  }
}

final class LightTypeTagInheritance(self: FLTT, other: FLTT) {
  private final val tpeNothing = NameReference("scala.Nothing")
  private final val tpeAny = NameReference("scala.Any")
  private final val tpeAnyRef = NameReference("scala.AnyRef")
  private final val tpeObject = NameReference("java.lang.Object")

  private lazy val ib: ImmutableMultiMap[NameReference, NameReference] = FLTT.mergeIDBs(self.idb, other.idb)
  private lazy val bdb: ImmutableMultiMap[AbstractReference, AbstractReference] = FLTT.mergeIDBs(self.basesdb, other.basesdb)


  def isChild(): Boolean = {
    val st = self.t
    val ot = other.t
    val logger = TrivialLogger.makeOut[this.type]("izumi.distage.debug.reflection", forceLog = true)

    logger.log(
      s"""Inheritance check: $self vs $other
         |bases: ${bdb.niceList()}
         |inheritance: ${ib.niceList()}
       """.stripMargin)

    isChild(Ctx(List.empty, logger))(st, ot)
  }

  implicit class CtxExt(val ctx: Ctx)  {
    def isChild(selfT0: LightTypeTag, thatT0: LightTypeTag): Boolean = LightTypeTagInheritance.this.isChild(ctx.next())(selfT0, thatT0)

  }
  
  private def isChild(ctx: Ctx)(selfT: LightTypeTag, thatT: LightTypeTag): Boolean = {
    import ctx._
    logger.log(s"️👀 $selfT <:< $thatT")
    
    
    val result = (selfT, thatT) match {
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
          oneOfKnownParentsIsInheritedFrom(ctx)(s, t) || shapeHeuristic(ctx)(s, t)
        }
      case (s: FullReference, t: NameReference) =>
        oneOfKnownParentsIsInheritedFrom(ctx)(s, t)
      case (s: NameReference, t: FullReference) =>
        oneOfKnownParentsIsInheritedFrom(ctx)(s, t)
      case (s: NameReference, t: NameReference) =>
        val boundIsOk = t.boundaries match {
          case Boundaries.Defined(bottom, top) =>
            ctx.isChild(s, top) && ctx.isChild(bottom, s)
          case Boundaries.Empty =>
            true
        }

        any(
          all(boundIsOk, parentsOf(s).exists(p => ctx.isChild(p, thatT))),
          all(boundIsOk, params.map(_.name).contains(t.ref)), // lambda parameter may accept anything
          s.boundaries match {
            case Boundaries.Defined(_, top) =>
              ctx.isChild(top, t)
            case Boundaries.Empty =>
              false
          }
        )


      case (_: AppliedNamedReference, t: Lambda) =>
        isChild(ctx.next(t.input))(selfT, t.output)
      case (s: Lambda, t: AppliedNamedReference) =>
        isChild(ctx.next(s.input))(s.output, t)
      case (s: Lambda, o: Lambda) =>
        s.input == o.input && isChild(ctx.next(s.input))(s.output, o.output)
      case (s: IntersectionReference, t: IntersectionReference) =>
        // yeah, this shit is quadratic
        s.refs.forall {
          c =>
            t.refs.exists {
              p =>
                ctx.isChild(c, p)
            }
        }
      case (s: IntersectionReference, t: LightTypeTag) =>
        s.refs.exists(c => ctx.isChild(c, t))
      case (s: LightTypeTag, o: IntersectionReference) =>
        o.refs.forall(t => ctx.isChild(s, t))

      case (s: Refinement, t: Refinement) =>
        ctx.isChild(s.reference, t.reference) && t.decls.diff(s.decls).isEmpty
      case (s: Refinement, t: LightTypeTag) =>
        ctx.isChild(s.reference, t)
      case (_: LightTypeTag, _: Refinement) =>
        false
    }
    logger.log(s"${if (result) "✅" else "⛔️"} $selfT <:< $thatT == $result")
    result
  }

  private def shapeHeuristic(ctx: Ctx)(self: FullReference, that: FullReference): Boolean = {
    def parameterShapeCompatible: Boolean = {
      self.parameters.zip(that.parameters).forall {
        case (ps, pt) =>
          ps.variance match {
            case Variance.Invariant =>
              ps.ref == pt.ref
            case Variance.Contravariant =>
              ctx.isChild(pt.ref, ps.ref)
            case Variance.Covariant =>
              ctx.isChild(ps.ref, pt.ref)
          }
      }
    }

    def sameArity: Boolean = {
      self.parameters.size == that.parameters.size
    }

    ctx.logger.log(s"⚠️ Heuristic required")

    sameArity && ctx.isChild(self.asName, that.asName) && parameterShapeCompatible
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
    nextNames.diff(tested)
      .foreach {
        b =>
          parentsOf(b.asName, out, tested)
      }

  }

  private def safeParentsOf(t: AbstractReference): Seq[AbstractReference] = {
    bdb.get(t).toSeq.flatten
  }

  private def oneOfKnownParentsIsInheritedFrom(ctx: Ctx)( child: AbstractReference, parent: AbstractReference): Boolean = {
    safeParentsOf(child).exists(p => ctx.isChild(p, parent))
  }

}
