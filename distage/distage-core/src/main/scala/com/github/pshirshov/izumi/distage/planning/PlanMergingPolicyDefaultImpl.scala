package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.exceptions.{ConflictingDIKeyBindingsException, SanityCheckFailedException, UnsupportedOpException}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp.ReferenceKey
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp._
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.planning.{PlanAnalyzer, PlanMergingPolicy}
import com.github.pshirshov.izumi.distage.model.reflection.SymbolIntrospector
import com.github.pshirshov.izumi.distage.planning.PlanMergingPolicyDefaultImpl.DIKeyConflictResolution
import com.github.pshirshov.izumi.fundamentals.graphs._
import distage.DIKey

import scala.collection.mutable

class PlanMergingPolicyDefaultImpl
(
  analyzer: PlanAnalyzer
  , symbolIntrospector: SymbolIntrospector.Runtime
) extends PlanMergingPolicy {

  override def extendPlan(currentPlan: DodgyPlan, binding: Binding, currentOp: NextOps): DodgyPlan = {
    (currentOp.provisions ++ currentOp.sets.values).foreach {
      op =>
        val target = op.target
        currentPlan.operations.addBinding(target, op)
    }

    currentPlan
  }

  override def finalizePlan(completedPlan: DodgyPlan): SemiPlan = {
    val resolved = completedPlan.operations.mapValues(resolveConflicts).toMap
    val allOperations = resolved.values.collect { case DIKeyConflictResolution.Successful(op) => op }.flatten.toSeq
    val issues = resolved.collect { case (k, DIKeyConflictResolution.Failed(ops)) => (k, ops) }.toMap

    if (issues.nonEmpty) {
      import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
      // TODO: issues == slots, we may apply slot logic here
      val issueRepr = issues.map {
        case (k, ops) =>
          s"Conflicting bindings found for key $k: ${ops.niceList().shift(2)}"
      }
      throw new ConflictingDIKeyBindingsException(
        s"""Multiple bindings bind to the same DIKey: ${issueRepr.niceList()}
           |There must be exactly one valid binding for each DIKey.
           |
           |You can use named instances: `make[X].named("id")` method and `distage.Id` annotation to disambiguate
           |between multiple instances of the same type.
         """.stripMargin
        , issues
      )
    }

    // it's not neccessary to sort the plan at this stage, it's gonna happen after GC
    SemiPlan(completedPlan.definition, allOperations.toVector, completedPlan.roots)
  }

  override def addImports(plan: SemiPlan): SemiPlan = {
    val topology = analyzer.topology(plan.steps)
    val imports = topology
      .dependees
      .graph
      .filterKeys(k => !plan.index.contains(k))
      .map {
        case (missing, refs) =>
          val maybeFirstOrigin = refs.headOption.flatMap(key => plan.index.get(key)).flatMap(_.origin)
          missing -> ImportDependency(missing, refs.toSet, maybeFirstOrigin)
      }
      .toMap

    SemiPlan(plan.definition, (imports.values ++ plan.steps).toVector, plan.roots)
  }

  override def reorderOperations(completedPlan: SemiPlan): OrderedPlan = {
    val topology = analyzer.topology(completedPlan.steps)

    val index = completedPlan.index

    def break(keys: Set[DIKey]): DIKey = {
      val loop = keys.toList

      val best = loop.sortWith {
        case (fst, snd) =>
          val fsto = index(fst)
          val sndo = index(snd)
          val fstp = symbolIntrospector.canBeProxied(fsto.target.tpe)
          val sndp = symbolIntrospector.canBeProxied(sndo.target.tpe)

          if (fstp && !sndp) {
            true
          } else if (!fstp) {
            false
          } else if (!fsto.isInstanceOf[ReferenceKey] && sndo.isInstanceOf[ReferenceKey]) {
            true
          } else if (fsto.isInstanceOf[ReferenceKey]) {
            false
          } else {
            val fstHasByName: Boolean = hasByNameParameter(fsto)
            val sndHasByName: Boolean = hasByNameParameter(sndo)

            if (!fstHasByName && sndHasByName) {
              true
            } else if (fstHasByName && !sndHasByName) {
              false
            } else {
              analyzer.requirements(fsto).size > analyzer.requirements(sndo).size
            }
          }

      }.head

      index(best) match {
        case op: ReferenceKey =>
          throw new UnsupportedOpException(s"Failed to break circular dependencies, best candidate $best is reference O_o: $keys", op)
        case op: ImportDependency =>
          throw new UnsupportedOpException(s"Failed to break circular dependencies, best candidate $best is import O_o: $keys", op)
        case op: ProxyOp =>
          throw new UnsupportedOpException(s"Failed to break circular dependencies, best candidate $best is proxy O_o: $keys", op)
        case op: InstantiationOp if !symbolIntrospector.canBeProxied(op.target.tpe) =>
          throw new UnsupportedOpException(s"Failed to break circular dependencies, best candidate $best is not proxyable (final?): $keys", op)
        case _: InstantiationOp =>
          best
      }
    }

    val sortedKeys = new Toposort().cycleBreaking(
      topology.dependencies.graph
      , Seq.empty
      , break
    ) match {
      case Left(value) =>
        throw new SanityCheckFailedException(s"Integrity check failed: cyclic reference not detected while it should be, ${value.issues}")

      case Right(value) =>
        value
    }

    val sortedOps = sortedKeys.flatMap(k => index.get(k).toSeq)

    OrderedPlan(completedPlan.definition, sortedOps.toVector, completedPlan.roots, topology)
  }

  protected def resolveConflicts(operations: mutable.Set[InstantiationOp]): DIKeyConflictResolution = {
    operations match {
      case s if s.nonEmpty && s.forall(_.isInstanceOf[CreateSet]) =>
        val ops = s.collect({ case c: CreateSet => c })
        val merged = ops.tail.foldLeft(ops.head) {
          case (acc, op) =>
            acc.copy(members = acc.members ++ op.members)
        }
        DIKeyConflictResolution.Successful(Set(merged))
      case s if s.size == 1 =>
        DIKeyConflictResolution.Successful(Set(s.head))
      case other =>
        DIKeyConflictResolution.Failed(other.toSet)
    }
  }

  private[this] def hasByNameParameter(fsto: ExecutableOp): Boolean = {
    val fstoTpe = ExecutableOp.instanceType(fsto)
    val ctorSymbol = symbolIntrospector.selectConstructorMethod(fstoTpe)
    val hasByName = ctorSymbol.exists(symbolIntrospector.hasByNameParameter)
    hasByName
  }

}

object PlanMergingPolicyDefaultImpl {

  sealed trait DIKeyConflictResolution

  object DIKeyConflictResolution {

    final case class Successful(op: Set[ExecutableOp]) extends DIKeyConflictResolution

    final case class Failed(ops: Set[InstantiationOp]) extends DIKeyConflictResolution
  }
}
