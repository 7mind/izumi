package com.github.pshirshov.izumi.idealingua.il.loader

import com.github.pshirshov.izumi.idealingua.model.il.ast.IDLTyper
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.CompletelyLoadedDomain
import com.github.pshirshov.izumi.idealingua.model.loader._
import com.github.pshirshov.izumi.idealingua.model.typespace.{TypespaceImpl, TypespaceVerifier}


class ModelResolverImpl(domainExt: String) extends ModelResolver {

  override def resolve(domains: UnresolvedDomains): LoadedModels = LoadedModels {
    val importResolver = new ExternalRefResolver(domains, domainExt)

    domains.domains.results
      .map(importResolver.resolveReferences)
      .map(makeTyped)
  }


  private def makeTyped(f: Either[LoadedDomain.Failure, CompletelyLoadedDomain]): LoadedDomain = {
    f.map(performTyping).fold(identity, identity)
  }

  private def performTyping(d: CompletelyLoadedDomain): LoadedDomain = {
    val ts: TypespaceImpl = runTyper(d)

    val issues = new TypespaceVerifier(ts).verify().toList
    if (issues.isEmpty) {
      LoadedDomain.Success(d.origin, ts)
    } else {
      LoadedDomain.TypingFailed(d.origin, d.id, issues)
    }
  }

  private def runTyper(d: CompletelyLoadedDomain): TypespaceImpl = {
    val domain = new IDLTyper(d).perform()
    val ts = TypespaceImpl(domain)
    ts
  }
}
