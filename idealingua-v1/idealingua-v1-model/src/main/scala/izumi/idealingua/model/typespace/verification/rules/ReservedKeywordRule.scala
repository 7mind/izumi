package izumi.idealingua.model.typespace.verification.rules

import izumi.idealingua.model.il.ast.typed.TypeDef
import izumi.idealingua.model.problems.{IDLDiagnostics, TypespaceWarning}
import izumi.idealingua.model.typespace.Typespace
import izumi.idealingua.model.typespace.verification.VerificationRule

class ReservedKeywordRule(context: String, keywords: Set[String]) extends VerificationRule {
  override def verify(ts: Typespace): IDLDiagnostics = {
    val warnings = ts.domain.types.flatMap {
      t =>
        val typename = if (keywords.contains(t.id.name)) {
          Seq(TypespaceWarning.ReservedKeywordName(t.id, context))
        } else {
          Seq.empty
        }

        val badMembers = t match {
          case TypeDef.Alias(_, _, _) =>
            Seq.empty
          case TypeDef.Enumeration(_, members, _) =>
            check(t, members.map(_.value))
          case TypeDef.Adt(_, alternatives, _) =>
            check(t, alternatives.map(_.typename))
          case TypeDef.Identifier(_, fields, _) =>
            check(t, fields.map(_.name))
          case structure: TypeDef.WithStructure =>
            check(t, structure.struct.fields.map(_.name))
        }

        typename ++ badMembers
    }

    IDLDiagnostics(Seq.empty, warnings)
  }

  private def check(t: TypeDef, names: List[String]): Seq[TypespaceWarning] = {
    val badNames = keywords.intersect(names.toSet)
    if (badNames.nonEmpty) {
      Seq(TypespaceWarning.ReservedKeywordField(t.id, context, badNames))
    } else {
      Seq.empty
    }
  }
}

object ReservedKeywordRule {
  def warning(context: String, keywords: Set[String]) = new ReservedKeywordRule(context, keywords)
}
