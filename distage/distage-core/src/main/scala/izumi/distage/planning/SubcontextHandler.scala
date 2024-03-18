package izumi.distage.planning

import izumi.distage.model.definition.errors.{LocalContextPlanningFailure, LocalContextVerificationFailure}
import izumi.distage.model.definition.{Binding, ImplDef}
import izumi.distage.model.plan.{Plan, Roots}
import izumi.distage.model.plan.Wiring.SingletonWiring
import izumi.distage.model.planning.{AxisPoint, PlanIssue}
import izumi.distage.model.{Planner, PlannerInput}
import izumi.distage.planning.solver.PlanVerifier
import izumi.distage.planning.solver.PlanVerifier.PlanVerifierResult
import izumi.fundamentals.collections.nonempty.NESet
import izumi.fundamentals.platform.functional.Identity

trait SubcontextHandler[+E] {
  def handle(binding: Binding, c: ImplDef.ContextImpl): Either[E, SingletonWiring]
}

object SubcontextHandler {
  class KnownActivationHandler(
    planner: Planner,
    input: PlannerInput,
  ) extends SubcontextHandler[LocalContextPlanningFailure] {
    override def handle(binding: Binding, c: ImplDef.ContextImpl): Either[LocalContextPlanningFailure, SingletonWiring] = {
      val roots = c.extractingFunction.diKeys.toSet
      for {
        subplan <- planner
          .plan(PlannerInput(c.module, input.activation, roots))
          .left.map(errors => LocalContextPlanningFailure(binding, c, errors))
      } yield {
        val allImported = subplan.importedKeys
        val importedParents = allImported.diff(c.externalKeys)
        SingletonWiring.PrepareSubcontext(c.extractingFunction, subplan, c.implType, c.externalKeys, importedParents)
      }
    }
  }

  class VerificationHandler(
    verifier: PlanVerifier,
    excludedActivations: Set[NESet[AxisPoint]],
  ) extends SubcontextHandler[LocalContextVerificationFailure] {
    override def handle(binding: Binding, c: ImplDef.ContextImpl): Either[LocalContextVerificationFailure, SingletonWiring] = {
      val roots = c.extractingFunction.diKeys.toSet
      val verifierResult = verifier.verify[Identity](c.module, Roots(roots), c.externalKeys, excludedActivations)

      verifierResult match {
        case incorrect: PlanVerifierResult.Incorrect =>
          val issues = incorrect.issues.value.toSet

          val missingImports = issues.collect {
            case issue: PlanIssue.MissingImport =>
              issue.key
          }

          if (issues.forall(_.isInstanceOf[PlanIssue.MissingImport])) {
            Right(SingletonWiring.PrepareSubcontext(c.extractingFunction, Plan.empty, c.implType, c.externalKeys, missingImports))
          } else {
            Left(LocalContextVerificationFailure(binding, c, issues))
          }

        case _: PlanVerifierResult.Correct =>
          Right(SingletonWiring.PrepareSubcontext(c.extractingFunction, Plan.empty, c.implType, c.externalKeys, Set.empty))
      }

    }
  }

  class TracingHandler() extends SubcontextHandler[Nothing] {
    override def handle(binding: Binding, c: ImplDef.ContextImpl): Either[Nothing, SingletonWiring] = {
      Right(SingletonWiring.PrepareSubcontext(c.extractingFunction, Plan.empty, c.implType, c.externalKeys, Set.empty))
    }
  }

}
