package com.github.pshirshov.izumi.idealingua.il.loader.verification

import com.github.pshirshov.izumi.idealingua.model.loader.LoadedDomain
import com.github.pshirshov.izumi.idealingua.model.problems.IDLDiagnostics

trait GlobalVerificationRule {
  def check(successful: Seq[LoadedDomain.Success]): IDLDiagnostics
}
