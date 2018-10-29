package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.exceptions.{UnsupportedOpException, UntranslatablePlanException}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp.ReferenceKey
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp._
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.planning.{PlanAnalyzer, PlanMergingPolicy}
import com.github.pshirshov.izumi.distage.model.reflection.SymbolIntrospector
import com.github.pshirshov.izumi.fundamentals.graphs._
import distage.DIKey

import scala.collection.mutable

sealed trait ConflictResolution

object ConflictResolution {

  final case class Successful(op: Set[ExecutableOp]) extends ConflictResolution

  final case class Failed(ops: Set[InstantiationOp]) extends ConflictResolution

}

class PlanMergingPolicyDefaultImpl(analyzer: PlanAnalyzer, symbolIntrospector: SymbolIntrospector.Runtime) extends PlanMergingPolicy {

  override def extendPlan(currentPlan: DodgyPlan, binding: Binding, currentOp: NextOps): DodgyPlan = {
    (currentOp.provisions ++ currentOp.sets.values).foreach {
      op =>
        val target = op.target
        currentPlan.operations.addBinding(target, op)
    }

    currentPlan
  }

  override def finalizePlan(completedPlan: DodgyPlan): SemiPlan = {
    val resolved = completedPlan.operations.mapValues(resolve).toMap
    val allOperations = resolved.values.collect({ case ConflictResolution.Successful(op) => op }).flatten.toSeq
    val issues = resolved.collect({ case (k, ConflictResolution.Failed(ops)) => (k, ops) }).toMap

    if (issues.nonEmpty) {
      // TODO: issues == slots, we may apply slot logic here
      throw new UntranslatablePlanException(s"Unresolved operation conflicts:\n${issues.mkString("\n")}", issues)
    }

    // it's not neccessary to sort the plan at this stage, it's gonna happen after GC
    SemiPlan(completedPlan.definition, allOperations.toVector)
  }

  def addImports(plan: SemiPlan): SemiPlan = {
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
    SemiPlan(plan.definition, (imports.values ++ plan.steps).toVector)
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

          if (fsto.isInstanceOf[ReferenceKey] && !sndo.isInstanceOf[ReferenceKey]) {
            false
          } else if (!fsto.isInstanceOf[ReferenceKey] && sndo.isInstanceOf[ReferenceKey]) {
            true
          } else if (fsto.isInstanceOf[ReferenceKey] && sndo.isInstanceOf[ReferenceKey]) {
            false
          } else {
            val fstHasByName: Boolean = hasByNameParameter(fsto)
            val sndHasByName: Boolean = hasByNameParameter(sndo)

            if (fstHasByName && !sndHasByName) {
              false
            } else if (!fstHasByName && sndHasByName) {
              true
            } else {
              analyzer.requirements(fsto).size > analyzer.requirements(sndo).size
            }
          }

      }.head

      index(best) match {
        case op: ReferenceKey =>
          throw new UnsupportedOpException(s"Failed to break circular dependencies, best candidate is reference O_o: $keys", op)
        case op: ImportDependency =>
          throw new UnsupportedOpException(s"Failed to break circular dependencies, best candidate is import O_o: $keys", op)
        case op: ProxyOp =>
          throw new UnsupportedOpException(s"Failed to break circular dependencies, best candidate is proxy O_o: $keys", op)
        case _: InstantiationOp =>
          best
      }
    }

    val sortedKeys = toposort.cycleBreaking(
      topology.dependencies.graph
      , Seq.empty
      , break
    )

    val sortedOps = sortedKeys.flatMap(k => index.get(k).toSeq)
    OrderedPlan(completedPlan.definition, sortedOps.toVector, topology)
  }

  private def hasByNameParameter(fsto: ExecutableOp): Boolean = {
    val fstoTpe = ExecutableOp.instanceType(fsto)
    val ctorSymbol = symbolIntrospector.selectConstructorMethod(fstoTpe)
    val hasByName = ctorSymbol.exists(symbolIntrospector.hasByNameParameter)
    hasByName
  }

  protected def resolve(operations: mutable.Set[InstantiationOp]): ConflictResolution = {
    operations match {
      case s if s.nonEmpty && s.forall(_.isInstanceOf[CreateSet]) =>
        val ops = s.collect({ case c: CreateSet => c })
        val merged = ops.tail.foldLeft(ops.head) {
          case (acc, op) =>
            acc.copy(members = acc.members ++ op.members)

        }
        ConflictResolution.Successful(Set(merged))
      case s if s.size == 1 =>
        ConflictResolution.Successful(Set(s.head))
      case other =>
        ConflictResolution.Failed(other.toSet)
    }
  }
}



