package izumi.distage.planning

import izumi.distage.model.exceptions.ConflictingDIKeyBindingsException
import izumi.distage.model.plan._
import izumi.distage.model.planning.PlanMergingPolicy
import izumi.distage.model.planning.PlanMergingPolicy.{DIKeyConflictResolution, WithResolve}
import izumi.fundamentals.platform.language.Quirks
import izumi.fundamentals.platform.strings.IzString._
import distage.DIKey

import scala.collection.mutable

class PlanMergingPolicyDefaultImpl extends PlanMergingPolicy with WithResolve {

  override final def freeze(plan: DodgyPlan): SemiPlan = {
    val resolved = mutable.HashMap[DIKey, Set[ExecutableOp]]()
    val issues = mutable.HashMap[DIKey, DIKeyConflictResolution.Failed]()

    plan.freeze.foreach {
      case (k, v) =>
        resolve(plan, k, v) match {
          case DIKeyConflictResolution.Successful(op) =>
            resolved.put(k, op)
          case f: DIKeyConflictResolution.Failed =>
            issues.put(k, f)
        }
    }

    if (issues.nonEmpty) {
      handleIssues(plan, resolved.toMap, issues.toMap)
    } else {
      SemiPlan(resolved.values.flatten.toVector, plan.gcMode)
    }
  }


  protected def handleIssues(plan: DodgyPlan, resolved: Map[DIKey, Set[ExecutableOp]], issues: Map[DIKey, DIKeyConflictResolution.Failed]): SemiPlan = {
    Quirks.discard(plan, resolved)
    throwOnIssues(issues)
  }

  protected final def throwOnIssues(issues: Map[distage.DIKey, DIKeyConflictResolution.Failed]): Nothing = {
    val issueRepr = formatIssues(issues)

    throw new ConflictingDIKeyBindingsException(
      s"""There must be exactly one valid binding for each DIKey.
         |
         |You can use named instances: `make[X].named("id")` method and `distage.Id` annotation to disambiguate
         |between multiple instances of the same type.
         |
         |List of problematic bindings: $issueRepr
         """.stripMargin
      , issues
    )
  }

  protected final def formatIssues(issues: Map[DIKey, DIKeyConflictResolution.Failed]): String = {
    issues
      .map {
        case (k, f) =>
          s"""Conflict resolution failed key $k with reason
           |
           |${f.explanation.shift(4)}
           |
           |    Candidates left: ${f.candidates.niceList().shift(4)}""".stripMargin
      }
      .niceList()
  }
}
