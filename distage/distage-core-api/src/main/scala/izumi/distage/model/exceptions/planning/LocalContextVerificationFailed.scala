package izumi.distage.model.exceptions.planning

import izumi.distage.model.definition.ImplDef
import izumi.distage.model.exceptions.DIException
import izumi.distage.model.planning.PlanIssue

class LocalContextVerificationFailed(message: String, val context: ImplDef.ContextImpl, val issues: Set[PlanIssue]) extends DIException(message)
