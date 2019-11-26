package izumi.idealingua.model.typespace.verification

import izumi.idealingua.model.problems.IDLDiagnostics
import izumi.idealingua.model.typespace.Typespace
import izumi.idealingua.model.typespace.verification.rules._

class TypespaceVerifier(ts: Typespace, rules: Seq[VerificationRule]) {
  def verify(): IDLDiagnostics = {
    val basicRules = Vector(
      DuplicateMemberRule,
      AdtMembersRule,
      BasicNamingConventionsRule,
      AdtConflictsRule,
      CyclicUsageRule,
      CyclicInheritanceRule,
      CyclicImportsRule.auto(ts),
    )

    val additional = (basicRules ++ rules).map(_.verify(ts))

    additional.fold(IDLDiagnostics.empty)(_ ++ _)
  }

}

object TypespaceVerifier {}
