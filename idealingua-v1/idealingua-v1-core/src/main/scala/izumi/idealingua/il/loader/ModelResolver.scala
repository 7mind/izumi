package izumi.idealingua.il.loader

import izumi.fundamentals.platform.exceptions.IzThrowable._
import izumi.idealingua.il.loader.verification.DuplicateDomainsRule
import izumi.idealingua.model.il.ast.IDLTyper
import izumi.idealingua.model.il.ast.raw.domains.DomainMeshResolved
import izumi.idealingua.model.loader._
import izumi.idealingua.model.problems.IDLDiagnostics
import izumi.idealingua.model.problems.TypespaceError.VerificationException
import izumi.idealingua.model.typespace.verification.{TypespaceVerifier, VerificationRule}
import izumi.idealingua.model.typespace.{Typespace, TypespaceImpl}


class ModelResolver(rules: Seq[VerificationRule]) {

  def resolve(domains: UnresolvedDomains): LoadedModels = {
    val globalChecks = Seq(
      DuplicateDomainsRule
    )
    val importResolver = new ExternalRefResolver(domains)

    val typed = domains.domains.results
      .map(importResolver.resolveReferences)
      .map(makeTyped)

    val result = LoadedModels(typed, IDLDiagnostics.empty)

    val postDiag = globalChecks.map(_.check(result.successful)).fold(IDLDiagnostics.empty)(_ ++ _)

    result.withDiagnostics(postDiag)
  }


  private def makeTyped(f: Either[LoadedDomain.Failure, DomainMeshResolved]): LoadedDomain = {
    (for {
      d <- f
      ts <- runTyper(d)
      result <- runVerifier(ts)
    } yield {
      result
    }).fold(identity, identity)
  }


  private def runVerifier(ts: Typespace): Either[LoadedDomain.VerificationFailed, LoadedDomain.Success] = {
    try {
      val issues = new TypespaceVerifier(ts, rules).verify()
      if (issues.issues.isEmpty) {
        Right(LoadedDomain.Success(ts.domain.meta.origin, ts, issues.warnings))
      } else {
        Left(LoadedDomain.VerificationFailed(ts.domain.meta.origin, ts.domain.id, issues))
      }
    } catch {
      case t: Throwable =>
        Left(LoadedDomain.VerificationFailed(ts.domain.meta.origin, ts.domain.id, IDLDiagnostics(Vector(VerificationException(t.stackTrace)))))
    }
  }

  private def runTyper(d: DomainMeshResolved): Either[LoadedDomain.TyperFailed, TypespaceImpl] = {
    (for {
      domain <- new IDLTyper(d).perform()
    } yield {
      new TypespaceImpl(domain)
    }).fold(issues => Left(LoadedDomain.TyperFailed(d.origin, d.id, issues)), Right.apply)
  }
}
