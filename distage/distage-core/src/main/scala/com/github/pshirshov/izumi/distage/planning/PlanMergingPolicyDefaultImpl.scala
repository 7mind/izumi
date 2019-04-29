package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.exceptions.ConflictingDIKeyBindingsException
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.planning.PlanMergingPolicy
import com.github.pshirshov.izumi.distage.model.planning.PlanMergingPolicy.{DIKeyConflictResolution, WithResolve}


class PlanMergingPolicyDefaultImpl() extends PlanMergingPolicy with WithResolve {

  override final def freeze(completedPlan: DodgyPlan): SemiPlan = {
    val resolved = completedPlan.freeze.map({case (k, v) => k -> resolve(k, v)}).toMap

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

    // it's not neccessary to sort the plan at this stage, it's gonna happen after GC
    SemiPlan(completedPlan.definition, allOperations.toVector, completedPlan.roots)
  }



}
