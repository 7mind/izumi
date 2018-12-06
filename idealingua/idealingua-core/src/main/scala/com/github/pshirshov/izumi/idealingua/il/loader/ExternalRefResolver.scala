package com.github.pshirshov.izumi.idealingua.il.loader

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.CompletelyLoadedDomain
import com.github.pshirshov.izumi.idealingua.model.loader._


private[loader] class ExternalRefResolver(domains: UnresolvedDomains) {

  def resolveReferences(domain: DomainParsingResult): Either[LoadedDomain.Failure, CompletelyLoadedDomain] = {
    new ExternalRefResolverPass(domains).resolveReferences(domain)
  }

}
