package com.github.pshirshov.izumi.idealingua.model.typespace.verification.rules

import com.github.pshirshov.izumi.idealingua.model.common.{Builtin, TypeId}
import com.github.pshirshov.izumi.idealingua.model.problems.{IDLDiagnostics, TypespaceError}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.Adt
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.model.problems.TypespaceError.AmbigiousAdtMember
import com.github.pshirshov.izumi.idealingua.model.typespace.verification.VerificationRule

import scala.collection.mutable

object AdtConflictsRule extends VerificationRule {
  override def verify(ts: Typespace): IDLDiagnostics = IDLDiagnostics {
    ts.domain.types.flatMap(checkAdtConflicts(ts, _))
  }

  private def checkAdtConflicts(ts: Typespace, definition: TypeDef): Seq[TypespaceError] = {
    definition match {
      case d: Adt =>
        val seen = mutable.HashMap.empty[TypeId, Int]
        checkAdtConflicts(ts, definition, mutable.HashSet.empty, seen)
        val conflicts = seen.filter(_._2 > 1).keys

        if (conflicts.isEmpty) {
          Seq.empty
        } else {
          Seq(AmbigiousAdtMember(d.id, conflicts.toList))
        }

      case _ => Seq.empty
    }
  }

  private def checkAdtConflicts(ts: Typespace, definition: TypeDef, visited: mutable.HashSet[TypeId], seen: mutable.HashMap[TypeId, Int]): Unit = definition match {
    case d: Adt =>
      d.alternatives.map(_.typeId).foreach {
        id =>
          seen.put(id, seen.getOrElseUpdate(id, 0) + 1)
      }

      visited.add(d.id)

      d.alternatives.filterNot(a => visited.contains(a.typeId)).filterNot(_.typeId.isInstanceOf[Builtin]).map(v => ts.apply(v.typeId)).foreach {
        defn =>
          checkAdtConflicts(ts, defn, visited, seen)
      }

    case _ =>
  }
}
