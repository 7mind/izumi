package com.github.pshirshov.izumi.idealingua.il.loader

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.problems.RefResolverIssue
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL.ILImport
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.{CompletelyLoadedDomain, IL}
import com.github.pshirshov.izumi.idealingua.model.loader._
import com.github.pshirshov.izumi.idealingua.model.parser.{ParsedDomain, ParsedModel}

import scala.collection.mutable


private[loader] class ExternalRefResolverPass(domains: UnresolvedDomains) {
  // we need mutable state to handle cyclic references (even though they aren't supported by go we still handle them)
  private val processed = mutable.HashMap[DomainId, CompletelyLoadedDomain]()

  def resolveReferences(domain: DomainParsingResult): Either[LoadedDomain.Failure, CompletelyLoadedDomain] = {
    domain match {
      case DomainParsingResult.Success(path, parsed) =>
        handleSuccess(path, parsed)
          .fold(issues => Left(LoadedDomain.ResolutionFailed(path, parsed.did, issues)), d => Right(d))

      case DomainParsingResult.Failure(path, message) =>
        Left(LoadedDomain.ParsingFailed(path, message))
    }
  }

  private def handleSuccess(domainPath: FSPath, parsed: ParsedDomain): Either[Vector[RefResolverIssue], CompletelyLoadedDomainMutable] = {
    (for {
      withIncludes <- resolveIncludes(parsed)
      loaded = new CompletelyLoadedDomainMutable(parsed.did, withIncludes, domainPath, parsed.model.includes, processed, parsed.imports.map(_.id).toSet)
    } yield {
      processed.update(parsed.did, loaded)

      val allImportIssues = parsed.imports
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
                      Left(Vector(RefResolverIssue.UnresolvableImport(parsed.did, imprt.id, v)))
                    case Right(v) =>
                      Right(v)
                  }

                case None =>
                  val diagnostics = domains.domains.results.map {
                    case DomainParsingResult.Success(path, domain) =>
                      s"OK: ${domain.did} at $path"

                    case DomainParsingResult.Failure(path, message) =>
                      s"KO: $path, problem: $message"

                  }
                  Left(Vector(RefResolverIssue.MissingImport(parsed.did, imprt.id, diagnostics.toList)))
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

  private def resolveIncludes(parsed: ParsedDomain): Either[Vector[RefResolverIssue], List[IL.Val]] = {
    val m = parsed.model
    val allIncludes = m.includes
      .map(i => loadModel(parsed.did, i, Seq(i)))

    for {
      model <- merge(m, allIncludes)
      importOps = parsed.imports.flatMap {
        i =>
          i.identifiers.map(ILImport(i.id, _))
      }
    } yield {
      val withIncludes = model.definitions ++ importOps
      withIncludes.toList
    }
  }

  private def loadModel(forDomain: DomainId, includePath: String, stack: Seq[String]): Either[Vector[RefResolverIssue], LoadedModel] = {
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

  private def findModel(forDomain: DomainId, domains: UnresolvedDomains, includePath: String): Option[ModelParsingResult] = {
    val absolute = FSPath(includePath)
    val prefixed = FSPath("idealingua" +: includePath.split("/"))
    val relativeToDomain = FSPath(forDomain.toPackage.init ++ includePath.split("/"))

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
      .filter(_.domain.did == include)

    if (matching.size > 1) {
      Left(RefResolverIssue.DuplicatedDomainsDuringLookup(include, matching.map(_.path).toList))
    } else {
      Right(matching.headOption)
    }

  }
}
