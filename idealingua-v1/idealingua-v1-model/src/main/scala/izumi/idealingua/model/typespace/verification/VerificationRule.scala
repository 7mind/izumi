package izumi.idealingua.model.typespace.verification

import izumi.idealingua.model.problems.IDLDiagnostics
import izumi.idealingua.model.typespace.Typespace

trait VerificationRule {
  def verify(ts: Typespace): IDLDiagnostics
}
