package izumi.distage.model.definition.errors

import izumi.distage.model.definition.ImplDef
import izumi.distage.model.planning.PlanIssue

sealed trait LocalContextFailure
object LocalContextFailure {
  final case class SubplanningFailure(impl: ImplDef.ContextImpl, errors: List[DIError]) extends LocalContextFailure
  final case class VerificationFailure(impl: ImplDef.ContextImpl, errors: Set[PlanIssue]) extends LocalContextFailure
}
