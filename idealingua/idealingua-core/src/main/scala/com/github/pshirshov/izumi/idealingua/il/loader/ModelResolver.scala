package com.github.pshirshov.izumi.idealingua.il.loader

import com.github.pshirshov.izumi.idealingua.il.loader.verification.DuplicateDomainsRule
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.domains.DomainMeshResolved
import com.github.pshirshov.izumi.idealingua.model.loader._
import com.github.pshirshov.izumi.idealingua.model.problems.IDLDiagnostics
import com.github.pshirshov.izumi.idealingua.typer2.{Typer2, TyperOptions}
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail

import scala.util.{Failure, Success, Try}


class ModelResolver(options: TyperOptions) {

  def resolve(domains: UnresolvedDomains): LoadedModels = {
    val globalChecks = Seq(
      DuplicateDomainsRule
    )
    val importResolver = new ExternalRefResolver(domains)

    val typed = domains.domains.results
      .sortBy(_.path.toString)
      .map(importResolver.resolveReferences)
      .map(runTyper)

    val result = LoadedModels(typed, IDLDiagnostics.empty)

    val postDiag = globalChecks.map(_.check(result.successful)).fold(IDLDiagnostics.empty)(_ ++ _)

    result.withDiagnostics(postDiag)
  }


  private def runTyper(maybeRaw: Either[LoadedDomain.Failure, DomainMeshResolved]): LoadedDomain = {
    for {
      raw <- maybeRaw
      typer = new Typer2(options, raw)
      typed <- Try {
        typer.run().left.map(issues => LoadedDomain.TyperFailed(raw.origin, raw.id, issues.errors, issues.warnings))
      } match {
        case Failure(exception) =>
          System.out.println(s"  ... !!! Typer2 failed on ${raw.id}:\n >> ${exception.getMessage}")
          exception.printStackTrace()
          Left(LoadedDomain.TyperFailed(raw.origin, raw.id, List(T2Fail.UnexpectedException(exception)), List.empty))
        case Success(value) =>
          value
      }
    } yield {
      LoadedDomain.Success(typed)
    }
  }.fold(identity, identity)
}
