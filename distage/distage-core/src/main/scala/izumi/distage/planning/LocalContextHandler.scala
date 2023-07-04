package izumi.distage.planning

import distage.Roots
import izumi.distage.model.definition.ImplDef
import izumi.distage.model.definition.errors.LocalContextFailure
import izumi.distage.model.plan.Wiring.SingletonWiring
import izumi.distage.model.planning.{AxisPoint, PlanIssue}
import izumi.distage.model.{Planner, PlannerInput}
import izumi.distage.planning.solver.PlanVerifier
import izumi.distage.planning.solver.PlanVerifier.PlanVerifierResult
import izumi.fundamentals.collections.nonempty.NonEmptySet
import izumi.fundamentals.platform.functional.Identity

trait LocalContextHandler {
  def handle(c: ImplDef.ContextImpl): Either[LocalContextFailure, SingletonWiring]
}

object LocalContextHandler {
  class KnownActivationHandler(planner: Planner, input: PlannerInput) extends LocalContextHandler {
    override def handle(c: ImplDef.ContextImpl): Either[LocalContextFailure.SubplanningFailure, SingletonWiring] = {
      for {
        subplan <- planner.plan(PlannerInput(c.module, input.activation, c.function.get.diKeys.toSet)).left.map(errors => LocalContextFailure.SubplanningFailure(c, errors))
      } yield {
        val allImported = subplan.importedKeys
        val importedParents = allImported.diff(c.externalKeys)
        SingletonWiring.PrepareLocalContext(c.function, c.module, c.implType, c.externalKeys, importedParents)
      }
    }
  }

  class VerificationHandler(verifier: PlanVerifier, excludedActivations: Set[NonEmptySet[AxisPoint]]) extends LocalContextHandler {
    override def handle(c: ImplDef.ContextImpl): Either[LocalContextFailure.VerificationFailure, SingletonWiring] = {
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
            Left(LocalContextFailure.VerificationFailure(c, issues))
          }

        case _: PlanVerifierResult.Correct =>
          Right(SingletonWiring.PrepareLocalContext(c.function, c.module, c.implType, c.externalKeys, Set.empty))
      }

    }
  }
}
