package izumi.idealingua.model.typespace.verification.rules

import izumi.idealingua.model.problems.{IDLDiagnostics, TypespaceError}
import izumi.idealingua.model.typespace.Typespace
import izumi.idealingua.model.typespace.verification.VerificationRule

object BasicNamingConventionsRule extends VerificationRule {
  final val badNames = Set("Iz", "IRT", "IDL")

  override def verify(ts: Typespace): IDLDiagnostics = IDLDiagnostics {
    ts.domain.types.flatMap {
      t =>
        val singleChar = if (t.id.name.size < 2) {
          Seq(TypespaceError.ShortName(t.id))
        } else {
          Seq.empty
        }

        val noncapitalized = if (t.id.name.head.isLower) {
          Seq(TypespaceError.NoncapitalizedTypename(t.id))
        } else {
          Seq.empty
        }

        val reserved = if (badNames.exists(t.id.name.startsWith)) {
          Seq(TypespaceError.ReservedTypenamePrefix(t.id, badNames))
        } else {
          Seq.empty
        }

        singleChar ++
          noncapitalized ++
          reserved
    }
  }
}


