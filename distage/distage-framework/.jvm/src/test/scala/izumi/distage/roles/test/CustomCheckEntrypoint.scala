package izumi.distage.roles.test

import cats.effect.IO
import distage.DIKey
import izumi.distage.framework.model.PlanCheckInput
import izumi.distage.model.planning.AxisPoint
import izumi.distage.planning.solver.PlanVerifier
import izumi.distage.planning.solver.PlanVerifier.PlanVerifierResult
import izumi.fundamentals.collections.nonempty.NESet
import izumi.fundamentals.platform.strings.IzString.toRichIterable

object CustomCheckEntrypoint extends TestEntrypointPatchedLeakBase {
  override def customCheck(
    planVerifier: PlanVerifier,
    excludedActivations: Set[NESet[AxisPoint]],
    checkConfig: Boolean,
    planCheckInput: PlanCheckInput[IO],
  ): PlanVerifierResult = {
    val reachable = planVerifier.traceReachables(planCheckInput.module, planCheckInput.roots, planCheckInput.providedKeys, excludedActivations)
    val conflictKeys = planCheckInput.module.keys.filter {
      // filter out any set elements (to remove weak set elements)
      case _: DIKey.SetElementKey => false
      // make sure that all keys we are checking contain 'Conflict' in their short type name
      case other => other.tpe.tag.shortName.contains("Conflict")
    }
    val unused = conflictKeys -- reachable
    if (unused.nonEmpty) {
      throw new RuntimeException(s"Custom check failed, found unused bindings for following keys: ${unused.map(_.tpe.tag.repr).niceList()}")
    } else {
      PlanVerifierResult.empty
    }
  }
}
