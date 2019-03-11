package com.github.pshirshov.izumi.idealingua.il.loader.verification

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.loader.{FSPath, LoadedDomain}
import com.github.pshirshov.izumi.idealingua.model.problems.{IDLDiagnostics, PostError}

object DuplicateDomainsRule extends GlobalVerificationRule {
  override def check(successful: Seq[LoadedDomain.Success]): IDLDiagnostics = {
    val duplicates: Map[DomainId, Seq[FSPath]] = successful.map(s => s.typespace.domainId -> s.typespace.origin).groupBy(_._1).filter(_._2.size > 1).mapValues(_.map(_._2))

    if (duplicates.isEmpty) {
      IDLDiagnostics.empty
    } else {
      IDLDiagnostics(Vector(PostError.DuplicatedDomains(duplicates)))
    }
  }
}
