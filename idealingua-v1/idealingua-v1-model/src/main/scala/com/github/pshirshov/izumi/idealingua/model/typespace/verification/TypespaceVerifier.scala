package com.github.pshirshov.izumi.idealingua.model.typespace.verification

import com.github.pshirshov.izumi.idealingua.model.problems.IDLDiagnostics
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.model.typespace.verification.rules._


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

object TypespaceVerifier {
}
