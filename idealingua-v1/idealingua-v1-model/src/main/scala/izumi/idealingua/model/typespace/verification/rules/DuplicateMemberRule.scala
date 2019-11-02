package izumi.idealingua.model.typespace.verification.rules

import izumi.idealingua.model.problems.{IDLDiagnostics, TypespaceError}
import izumi.idealingua.model.il.ast.typed.TypeDef.{Adt, Enumeration}
import izumi.idealingua.model.typespace.Typespace
import izumi.idealingua.model.typespace.verification.VerificationRule

object DuplicateMemberRule extends VerificationRule {
  override def verify(ts: Typespace): IDLDiagnostics = IDLDiagnostics {
    ts.domain.types.flatMap {
      case t: Enumeration =>
        val duplicates = t.members.groupBy(v => v).filter(_._2.lengthCompare(1) > 0)
        if (duplicates.nonEmpty) {
          Seq(TypespaceError.DuplicateEnumElements(t.id, duplicates.keys.map(_.value).toList))
        } else {
          Seq.empty
        }

      case t: Adt =>
        val duplicates = t.alternatives.groupBy(v => v.typename).filter(_._2.lengthCompare(1) > 0)
        if (duplicates.nonEmpty) {
          Seq(TypespaceError.DuplicateAdtElements(t.id, duplicates.keys.toList))
        } else {
          Seq.empty
        }

      case _ =>
        Seq.empty
    }
  }
}
