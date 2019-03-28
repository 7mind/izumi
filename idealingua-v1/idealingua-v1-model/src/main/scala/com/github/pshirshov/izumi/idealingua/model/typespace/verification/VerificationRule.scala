package com.github.pshirshov.izumi.idealingua.model.typespace.verification

import com.github.pshirshov.izumi.idealingua.model.problems.IDLDiagnostics
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace

trait VerificationRule {
  def verify(ts: Typespace): IDLDiagnostics
}
