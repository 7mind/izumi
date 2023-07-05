package izumi.distage.model.definition.errors

import izumi.distage.model.definition.{Binding, ImplDef}
import izumi.distage.model.planning.PlanIssue

final case class LocalContextVerificationFailure(binding: Binding, impl: ImplDef.ContextImpl, errors: Set[PlanIssue])
