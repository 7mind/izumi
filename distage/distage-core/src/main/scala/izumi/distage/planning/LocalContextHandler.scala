package izumi.distage.planning

import distage.Roots
import izumi.distage.model.definition.errors.{LocalContextPlanningFailure, LocalContextVerificationFailure}
import izumi.distage.model.definition.{Binding, ImplDef}
import izumi.distage.model.plan.Wiring.SingletonWiring
import izumi.distage.model.planning.{AxisPoint, PlanIssue}
import izumi.distage.model.{Planner, PlannerInput}
import izumi.distage.planning.solver.PlanVerifier
import izumi.distage.planning.solver.PlanVerifier.PlanVerifierResult
import izumi.fundamentals.collections.nonempty.NESet
import izumi.fundamentals.platform.functional.Identity

trait LocalContextHandler[+E] {
  def handle(binding: Binding, c: ImplDef.ContextImpl): Either[E, SingletonWiring]
}

object LocalContextHandler {
  class KnownActivationHandler(planner: Planner, input: PlannerInput) extends LocalContextHandler[LocalContextPlanningFailure] {
    override def handle(binding: Binding, c: ImplDef.ContextImpl): Either[LocalContextPlanningFailure, SingletonWiring] = {
      for {
        subplan <- planner
          .plan(PlannerInput(c.module, input.activation, c.function.get.diKeys.toSet)).left.map(errors => LocalContextPlanningFailure(binding, c, errors))
      } yield {
        val allImported = subplan.importedKeys
        val importedParents = allImported.diff(c.externalKeys)
        SingletonWiring.PrepareLocalContext(c.function, c.module, c.implType, c.externalKeys, importedParents)
      }
    }
  }

  class VerificationHandler(verifier: PlanVerifier, excludedActivations: Set[NESet[AxisPoint]]) extends LocalContextHandler[LocalContextVerificationFailure] {
    override def handle(binding: Binding, c: ImplDef.ContextImpl): Either[LocalContextVerificationFailure, SingletonWiring] = {
      val ver = verifier.verify[Identity](c.module, Roots(c.function.get.diKeys.toSet), k => c.externalKeys.contains(k), excludedActivations)

      ver match {
        case incorrect: PlanVerifierResult.Incorrect =>
          val issues = incorrect.issues.value.toSet

          val missingImports = issues.collect {
            case issue: PlanIssue.MissingImport =>
              issue.key
          }

          if (issues.forall(_.isInstanceOf[PlanIssue.MissingImport])) {
            Right(SingletonWiring.PrepareLocalContext(c.function, c.module, c.implType, c.externalKeys, missingImports))
          } else {
            Left(LocalContextVerificationFailure(binding, c, issues))
          }

        case _: PlanVerifierResult.Correct =>
          Right(SingletonWiring.PrepareLocalContext(c.function, c.module, c.implType, c.externalKeys, Set.empty))
      }

    }
  }
}
