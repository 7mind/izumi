package com.github.pshirshov.izumi.idealingua.il.loader

import com.github.pshirshov.izumi.idealingua.model.il.ast.IDLTyper
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.CompletelyLoadedDomain
import com.github.pshirshov.izumi.idealingua.model.loader._
import com.github.pshirshov.izumi.idealingua.model.typespace.TypespaceVerificationIssue.VerificationException
import com.github.pshirshov.izumi.idealingua.model.typespace.{TypespaceImpl, TypespaceVerifier}
import com.github.pshirshov.izumi.fundamentals.platform.exceptions.IzThrowable._


class ModelResolverImpl() extends ModelResolver {

  override def resolve(domains: UnresolvedDomains): LoadedModels = LoadedModels {
    val importResolver = new ExternalRefResolver(domains)

    domains.domains.results
      .map(importResolver.resolveReferences)
      .map(makeTyped)
  }


  private def makeTyped(f: Either[LoadedDomain.Failure, CompletelyLoadedDomain]): LoadedDomain = {
    (for {
      d <- f
      ts <- runTyper(d)
    } yield {
      runVerifier(d, ts)
    }).fold(identity, maybe => maybe.fold(identity, identity))
  }


  private def runVerifier(d: CompletelyLoadedDomain, ts: TypespaceImpl) = {
    try {
      val issues = new TypespaceVerifier(ts).verify().toList
      if (issues.isEmpty) {
        Right(LoadedDomain.Success(d.origin, ts))
      } else {
        Left(LoadedDomain.VerificationFailed(d.origin, d.id, issues))
      }
    } catch {
      case t: Throwable =>
        Left(LoadedDomain.VerificationFailed(d.origin, d.id, List(VerificationException(t.stackTrace))))
    }
  }

  private def runTyper(d: CompletelyLoadedDomain): Either[LoadedDomain.TyperFailed, TypespaceImpl] = {
    (for {
      domain <- new IDLTyper(d).perform()
    } yield {
      new TypespaceImpl(domain)
    }).fold(issues => Left(LoadedDomain.TyperFailed(d.origin, d.id, issues)), v => Right(v))
  }
}
