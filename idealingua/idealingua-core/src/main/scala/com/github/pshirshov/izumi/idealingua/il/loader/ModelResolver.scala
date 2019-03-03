package com.github.pshirshov.izumi.idealingua.il.loader

import com.github.pshirshov.izumi.fundamentals.platform.exceptions.IzThrowable
import com.github.pshirshov.izumi.fundamentals.platform.exceptions.IzThrowable._
import com.github.pshirshov.izumi.idealingua.il.loader.verification.DuplicateDomainsRule
import com.github.pshirshov.izumi.idealingua.model.il.ast.IDLTyper
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.domains.DomainMeshResolved
import com.github.pshirshov.izumi.idealingua.model.loader._
import com.github.pshirshov.izumi.idealingua.model.problems.IDLDiagnostics
import com.github.pshirshov.izumi.idealingua.model.problems.TypespaceError.VerificationException
import com.github.pshirshov.izumi.idealingua.model.typespace.verification.{TypespaceVerifier, VerificationRule}
import com.github.pshirshov.izumi.idealingua.model.typespace.{Typespace, TypespaceImpl}
import com.github.pshirshov.izumi.idealingua.typer2.Typer2


class ModelResolver(rules: Seq[VerificationRule]) {

  def resolve(domains: UnresolvedDomains, runt2: Boolean): LoadedModels = {
    val globalChecks = Seq(
      DuplicateDomainsRule
    )
    val importResolver = new ExternalRefResolver(domains)

    val typed = domains.domains.results
      .sortBy(_.path.toString)
      .map(importResolver.resolveReferences)
      .map {
        m =>
          if (runt2) {
            m.foreach {
              defn =>
                try {
                  val t2 = new Typer2(defn)
                  t2.run()
                } catch {
                  case t: Throwable =>
                    import IzThrowable._
                    //System.out.println(s"Typer2 failed on ${defn.id}: ${t.stackTrace}")
                    System.out.println(s"Typer2 failed on ${defn.id}:\n >> ${t.getMessage}")
                }
            }
          }

          m
      }
      .map(makeTyped)

    val result = LoadedModels(typed, IDLDiagnostics.empty)

    val postDiag = globalChecks.map(_.check(result.successful)).fold(IDLDiagnostics.empty)(_ ++ _)

    result.withDiagnostics(postDiag)
  }


  private def makeTyped(f: Either[LoadedDomain.Failure, DomainMeshResolved]): LoadedDomain = {
    LoadedDomain.XXX()
//    (for {
//      d <- f
//      ts <- runTyper(d)
//      result <- runVerifier(ts)
//    } yield {
//      result
//    }).fold(identity, identity)
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
