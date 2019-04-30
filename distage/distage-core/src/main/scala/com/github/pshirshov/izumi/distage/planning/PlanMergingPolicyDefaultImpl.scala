package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.exceptions.ConflictingDIKeyBindingsException
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.planning.PlanMergingPolicy
import com.github.pshirshov.izumi.distage.model.planning.PlanMergingPolicy.{DIKeyConflictResolution, WithResolve}
import distage.DIKey


class PlanMergingPolicyDefaultImpl() extends PlanMergingPolicy with WithResolve {

  override final def freeze(completedPlan: DodgyPlan): SemiPlan = {
    val resolved = completedPlan.freeze.map({case (k, v) => k -> resolve(completedPlan, k, v)}).toMap

    val allOperations = resolved.values.collect { case DIKeyConflictResolution.Successful(op) => op }.flatten.toSeq
    val issues = resolved.collect { case (k, f: DIKeyConflictResolution.Failed) => (k, f) }.toMap

    if (issues.nonEmpty) {
      handleIssues(issues)
    } else {
      SemiPlan(completedPlan.definition, allOperations.toVector, completedPlan.roots)
    }
  }


  def handleIssues(issues: Map[DIKey, DIKeyConflictResolution.Failed]): SemiPlan = {
    import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
    // TODO: issues == slots, we may apply slot logic here
    val issueRepr = issues.map {
      case (k, f) =>
        s"""Conflict resolution failed key $k with reason
           |
           |${f.explanation.shift(4)}
           |
           |    Candidates left: ${f.ops.niceList().shift(4)}""".stripMargin
    }

    throw new ConflictingDIKeyBindingsException(
      s"""There must be exactly one valid binding for each DIKey.
         |
         |You can use named instances: `make[X].named("id")` method and `distage.Id` annotation to disambiguate
         |between multiple instances of the same type.
         |
         |List of problematic bindings: ${issueRepr.niceList()}
         """.stripMargin
      , issues
    )
  }
}
