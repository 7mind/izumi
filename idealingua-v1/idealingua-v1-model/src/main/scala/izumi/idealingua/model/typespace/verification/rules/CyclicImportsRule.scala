package izumi.idealingua.model.typespace.verification.rules

import izumi.idealingua.model.common.DomainId
import izumi.idealingua.model.problems.TypespaceWarning.DomainInvolvedIntoCyclicImports
import izumi.idealingua.model.problems.{IDLDiagnostics, TypespaceError}
import izumi.idealingua.model.typespace.Typespace
import izumi.idealingua.model.typespace.verification.VerificationRule

import scala.collection.mutable

class CyclicImportsRule(onLoop: (Typespace, Set[Seq[DomainId]]) => IDLDiagnostics) extends VerificationRule {
  override def verify(ts: Typespace): IDLDiagnostics = {
    val loops = mutable.HashSet.empty[Seq[DomainId]]
    if (hasCycles(ts, Seq(ts.domain.id), loops, Set.empty)) {
      onLoop(ts, loops.toSet)
    } else {
      IDLDiagnostics.empty
    }
  }

  private def hasCycles(ts: Typespace, path: Seq[DomainId], loops: scala.collection.mutable.HashSet[Seq[DomainId]], seen: Set[DomainId]): Boolean = {
    val currentId = ts.domain.id
    if (seen.contains(currentId)) {
      loops.add(path)
      true
    } else {
      ts.domain
        .referenced
        .values
        .map(_.perform())
        .exists(r => hasCycles(ts.transitivelyReferenced(r.id), path :+ r.id, loops, seen + currentId))
    }
  }
}

object CyclicImportsRule {
  def warning(): CyclicImportsRule = {
    new CyclicImportsRule((ts, loops) => IDLDiagnostics(Seq(), Seq(DomainInvolvedIntoCyclicImports(ts.domain.id, loops))))
  }

  def error(clue: String): CyclicImportsRule = {
    new CyclicImportsRule((ts, loops) => IDLDiagnostics(Seq(TypespaceError.DomainInvolvedIntoCyclicImports(ts.domain.id, loops, clue)), Seq()))
  }

  def auto(ts: Typespace): CyclicImportsRule = {
    if (ts.domain.meta.meta.annos.exists(_.name.toLowerCase == "nonportable")) {
      CyclicImportsRule.warning()
    } else {
      CyclicImportsRule.error("such a domain must be marked with @nonportable() annotation")
    }
  }
}
