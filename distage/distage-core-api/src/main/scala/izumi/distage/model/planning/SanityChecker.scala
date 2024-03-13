package izumi.distage.model.planning

import izumi.distage.model.definition.errors.DIError
import izumi.distage.model.plan.*
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.collections.nonempty.NEList
import izumi.fundamentals.graphs.DG

trait SanityChecker {
  def verifyPlan(plan: DG[DIKey, ExecutableOp], roots: Roots): Either[NEList[DIError.VerificationError], Unit]
}
