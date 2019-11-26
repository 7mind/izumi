package izumi.idealingua.model.typespace.verification.rules

import izumi.idealingua.model.common.Builtin
import izumi.idealingua.model.common.TypeId.AliasId
import izumi.idealingua.model.problems.{IDLDiagnostics, TypespaceError}
import izumi.idealingua.model.il.ast.typed.AdtMember
import izumi.idealingua.model.il.ast.typed.TypeDef.Adt
import izumi.idealingua.model.typespace.Typespace
import izumi.idealingua.model.typespace.verification.VerificationRule

object AdtMembersRule extends VerificationRule {
  override def verify(ts: Typespace): IDLDiagnostics = IDLDiagnostics {
    ts.domain.types.flatMap {
      case t: Adt =>
        val builtins = t.alternatives.collect {
          case m @ AdtMember(_: Builtin, _, _) =>
            m
          case m @ AdtMember(a: AliasId, _, _) if ts.dealias(a).isInstanceOf[Builtin] =>
            m
        }
        if (builtins.nonEmpty) {
          Seq(TypespaceError.PrimitiveAdtMember(t.id, builtins))
        } else {
          Seq.empty
        }
      case _ =>
        Seq.empty
    }
  }
}
