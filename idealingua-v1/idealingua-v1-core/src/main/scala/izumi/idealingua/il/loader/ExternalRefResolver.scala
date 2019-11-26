package izumi.idealingua.il.loader

import izumi.idealingua.model.il.ast.raw.domains.DomainMeshResolved
import izumi.idealingua.model.loader._

private[loader] class ExternalRefResolver(domains: UnresolvedDomains) {

  def resolveReferences(domain: DomainParsingResult): Either[LoadedDomain.Failure, DomainMeshResolved] = {
    new ExternalRefResolverPass(domains).resolveReferences(domain)
  }

}
