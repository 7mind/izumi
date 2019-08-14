package izumi.idealingua.il.loader.verification

import izumi.idealingua.model.loader.LoadedDomain
import izumi.idealingua.model.problems.IDLDiagnostics

trait GlobalVerificationRule {
  def check(successful: Seq[LoadedDomain.Success]): IDLDiagnostics
}
