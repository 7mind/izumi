package com.github.pshirshov.izumi.idealingua.il.loader

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.domains.{DomainMeshResolved, ParsedDomain}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.models.{Inclusion, ParsedModel}
import com.github.pshirshov.izumi.idealingua.model.loader._
import com.github.pshirshov.izumi.idealingua.model.problems.RefResolverIssue

import scala.collection.mutable


private[loader] class ExternalRefResolverPass(domains: UnresolvedDomains) {
  // we need mutable state to handle cyclic references (even though they aren't supported by go we still handle them)
  private val processed = mutable.HashMap[DomainId, DomainMeshResolved]()

  def resolveReferences(domain: DomainParsingResult): Either[LoadedDomain.Failure, DomainMeshResolved] = {
    domain match {
      case DomainParsingResult.Success(path, parsed) =>
        handleSuccess(path, parsed)
          .fold(issues => Left(LoadedDomain.ResolutionFailed(path, parsed.decls.id, issues)), d => Right(d))

      case DomainParsingResult.Failure(path, message) =>
        Left(LoadedDomain.ParsingFailed(path, message))
    }
  }

  private def handleSuccess(domainPath: FSPath, parsed: ParsedDomain): Either[Vector[RefResolverIssue], DomainMeshResolvedMutable] = {
    (for {
      withIncludes <- resolveIncludes(parsed)
      loaded = new DomainMeshResolvedMutable(
        parsed.decls.id,
        withIncludes.model.definitions,
        domainPath,
        parsed.model.includes,
        parsed.decls.imports,
        parsed.decls.meta,
        processed,
        parsed.decls.imports.map(_.id).toSet
      )
    } yield {
      processed.update(parsed.decls.id, loaded)

      val allImportIssues = parsed.decls.imports
        .filterNot(i => processed.contains(i.id))
        .map {
          imprt =>

            for {
              imported <- findDomain(domains, imprt.id)
            } yield {
              imported match {
                case Some(value) =>
                  resolveReferences(value) match {
                    case Left(v) =>
                      Left(Vector(RefResolverIssue.UnresolvableImport(parsed.decls.id, imprt.id, v)))
                    case Right(v) =>
                      Right(v)
                  }

                case None =>
                  val diagnostics = domains.domains.results.map {
                    case DomainParsingResult.Success(path, domain) =>
                      s"OK: ${domain.decls.id} at $path"

                    case DomainParsingResult.Failure(path, message) =>
                      s"KO: $path, problem: $message"

                  }
                  Left(Vector(RefResolverIssue.MissingImport(parsed.decls.id, imprt.id, diagnostics.toList)))
              }
            }

        }
        .map {
          out =>
            out.fold(issue => Left(Vector(issue)), right => right.fold(issues => Left(issues), good => Right(good)))
        }
        .collect({ case Left(issues) => issues })
        .flatten
        .toVector

      if (allImportIssues.isEmpty) {
        Right(loaded)
      } else {
        Left(allImportIssues)
      }

    })
      .fold(issues => Left(issues), result => result.fold(issues => Left(issues), domain => Right(domain)))
  }

  private def resolveIncludes(parsed: ParsedDomain): Either[Vector[RefResolverIssue], ParsedDomain] = {
    val m = parsed.model
    val allIncludes = m.includes
      .map(i => loadModel(parsed.decls.id, i, Seq(i)))

    for {
      model <- merge(m, allIncludes)
    } yield {
      parsed.copy(model = parsed.model.copy(definitions = model.definitions.toList, includes = Seq.empty))
    }
  }

  private def loadModel(forDomain: DomainId, includePath: Inclusion, stack: Seq[Inclusion]): Either[Vector[RefResolverIssue], LoadedModel] = {
    findModel(forDomain, domains, includePath)
      .map {
        case ModelParsingResult.Success(_, model) =>
          val subincludes = model.includes
            .map(i => loadModel(forDomain, i, stack :+ i))

          merge(model, subincludes)

        case f: ModelParsingResult.Failure =>
          Left(Vector(RefResolverIssue.UnparseableInclusion(forDomain, stack.toList, f)))

      }
      .getOrElse {
        val diagnostic = domains.models.results.map {
          case ModelParsingResult.Success(path, _) =>
            s"OK: $path"
          case ModelParsingResult.Failure(path, message) =>
            s"KO: $path, problem: $message"
        }
        Left(Vector(RefResolverIssue.MissingInclusion(forDomain, stack.toList, includePath, diagnostic.toList)))
      }
  }

  private def merge(model: ParsedModel, subincludes: Seq[Either[Vector[RefResolverIssue], LoadedModel]]): Either[Vector[RefResolverIssue], LoadedModel] = {
    val issues = subincludes.collect({ case Left(subissues) => subissues }).flatten.toVector

    if (issues.nonEmpty) {
      Left(issues)
    } else {
      val submodels = subincludes.collect({ case Right(m) => m })
      val folded = submodels.fold(LoadedModel(model.definitions)) {
        case (acc, m) => acc ++ m
      }
      Right(folded)
    }
  }

  private def findModel(forDomain: DomainId, domains: UnresolvedDomains, includePath: Inclusion): Option[ModelParsingResult] = {
    val includeparts = includePath.i.split("/")
    val absolute = FSPath(includeparts)
    val prefixed = FSPath("idealingua" +: includeparts)
    val relativeToDomain = FSPath(forDomain.toPackage.init ++ includeparts)

    val candidates = Set(absolute, prefixed, relativeToDomain)
    domains.models.results.find(f => candidates.contains(f.path))
  }

  private def findDomain(domains: UnresolvedDomains, include: DomainId): Either[RefResolverIssue, Option[DomainParsingResult.Success]] = {
    val matching = domains.domains
      .results
      .collect {
        case s: DomainParsingResult.Success =>
          s
      }
      .filter(_.domain.decls.id == include)

    if (matching.size > 1) {
      Left(RefResolverIssue.DuplicatedDomainsDuringLookup(include, matching.map(_.path).toList))
    } else {
      Right(matching.headOption)
    }

  }
}
