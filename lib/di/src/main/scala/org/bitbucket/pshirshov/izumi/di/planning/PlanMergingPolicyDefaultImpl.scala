package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.definition.Binding
import org.bitbucket.pshirshov.izumi.di.model.plan.DodgyOp.{Nop, Statement}
import org.bitbucket.pshirshov.izumi.di.model.plan.ExecutableOp.AddToSet
import org.bitbucket.pshirshov.izumi.di.model.plan.PlanningConflict.{Conflict, NoConflict}
import org.bitbucket.pshirshov.izumi.di.model.plan.PlanningOp.{Put, SolveRedefinition, SolveUnsolvable}
import org.bitbucket.pshirshov.izumi.di.model.plan._

class PlanMergingPolicyDefaultImpl extends PlanMergingPolicy {
  def extendPlan(currentPlan: DodgyPlan, binding: Binding, currentOp: NextOps): DodgyPlan = {
    val newProvisions = currentOp
      .provisions
      .map(op => Statement(ExecutableOp.ImportDependency(op.target))).toSet


    val withConflicts = findConflicts(currentPlan.steps, currentOp.provisions)

    val (before, after) = splitCurrentPlan(currentPlan.steps, withConflicts)
    System.err.println(before.size, after.size)

    val transformations = withConflicts.map {
      case NoConflict(newOp) =>
        Put(newOp)

      case Conflict(newOp, existing) if Statement(newOp) == existing =>
        SolveRedefinition(newOp)

      case Conflict(newOp, existing) =>
        SolveUnsolvable(newOp, existing)
    }

    val transformationsWithoutReplacements = transformations.map {
      case t: Put => DodgyOp.Statement(t.op)
      case t: SolveRedefinition => DodgyOp.DuplicatedStatement(t.op)
      case t: SolveUnsolvable => DodgyOp.UnsolvableConflict(t.op, t.existing)
    }

    val safeNewProvisions: Seq[DodgyOp] = Seq(Nop(s"// head: $binding")) ++
      before ++
      Seq(Nop(s"// tran: $binding")) ++
      transformationsWithoutReplacements ++
      Seq(Nop(s"// tail: $binding")) ++
      after ++
      Seq(Nop(s"//  end: $binding"))

    val newImports = (currentPlan.imports ++ currentOp.imports.map(Statement)) -- newProvisions
    val newSets = currentPlan.sets ++ currentOp.sets.map(Statement)

    DodgyPlan(
      newImports
      , newSets
      , safeNewProvisions
    )
  }

  private def splitCurrentPlan(currentPlan: Seq[DodgyOp], withConflicts: Seq[PlanningConflict]): (Seq[DodgyOp], Seq[DodgyOp]) = {
    import PlanningConflict._
    val currentPlanBlock = currentPlan
    val insertAt = Seq(Seq(currentPlanBlock.length), withConflicts
      .map {
        case Conflict(_, op) =>
          currentPlanBlock.indexOf(op)

        case _ => // we don't consider all other cases
          currentPlanBlock.length
      })
      .flatten
      .min

    val (before, after) = currentPlanBlock.splitAt(insertAt)
    (before, after)
  }

  private def findConflicts(currentPlan: Seq[DodgyOp], toProvision: Seq[ExecutableOp]): Seq[PlanningConflict] = {
    import PlanningConflict._
    val currentUnwrapped = currentPlan.collect { case s: Statement => s }.toSet

    val withConflicts = toProvision.map {
      newOp =>
        // safe to use .find, plan itself cannot contain conflicts
        // TODO: ineffective!
        currentUnwrapped.find(dop => areConflicting(newOp, dop.op)) match {
          case Some(existing) =>
            Conflict(newOp, existing)
          case _ =>
            NoConflict(newOp)
        }
    }
    withConflicts
  }

  private def areConflicting(newOp: ExecutableOp, existingOp: ExecutableOp): Boolean = {
    (existingOp, newOp) match {
      case (eop: AddToSet, nop: AddToSet) if eop.target == nop.target =>
        false
      case (eop, nop) =>
        eop.target == nop.target
    }
  }
}
