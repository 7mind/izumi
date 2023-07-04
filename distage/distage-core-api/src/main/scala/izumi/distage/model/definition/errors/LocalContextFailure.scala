package izumi.distage.model.definition.errors

import izumi.distage.model.definition.{Binding, ImplDef}
import izumi.distage.model.planning.PlanIssue

sealed trait LocalContextFailure {
  def binding: Binding
  def impl: ImplDef.ContextImpl
}
object LocalContextFailure {
  final case class SubplanningFailure(binding: Binding, impl: ImplDef.ContextImpl, errors: List[DIError]) extends LocalContextFailure
  final case class VerificationFailure(binding: Binding, impl: ImplDef.ContextImpl, errors: Set[PlanIssue]) extends LocalContextFailure
}
