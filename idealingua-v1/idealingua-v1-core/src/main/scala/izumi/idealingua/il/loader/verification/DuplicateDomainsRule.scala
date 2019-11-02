package izumi.idealingua.il.loader.verification

import izumi.idealingua.model.common.DomainId
import izumi.idealingua.model.loader.{FSPath, LoadedDomain}
import izumi.idealingua.model.problems.{IDLDiagnostics, PostError}

object DuplicateDomainsRule extends GlobalVerificationRule {
  override def check(successful: Seq[LoadedDomain.Success]): IDLDiagnostics = {
    val duplicates: Map[DomainId, Seq[FSPath]] = successful.map(s => s.typespace.domain.id -> s.path).groupBy(_._1).filter(_._2.size > 1).mapValues(_.map(_._2)).toMap

    if (duplicates.isEmpty) {
      IDLDiagnostics.empty
    } else {
      IDLDiagnostics(Vector(PostError.DuplicatedDomains(duplicates)))
    }
  }
}
