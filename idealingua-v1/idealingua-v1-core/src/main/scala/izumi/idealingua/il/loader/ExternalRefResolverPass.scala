package izumi.idealingua.il.loader

import izumi.idealingua.model.common.DomainId
import izumi.idealingua.model.il.ast.raw.domains.{DomainMeshResolved, ParsedDomain}
import izumi.idealingua.model.il.ast.raw.models.{Inclusion, ParsedModel}
import izumi.idealingua.model.loader._
import izumi.idealingua.model.problems.RefResolverIssue

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
      withIncludes <- resolveIncludes(domainPath, parsed)
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
        .collect { case Left(issues) => issues }
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

  private def resolveOverlay(forDomain: DomainId)(result: ModelParsingResult): Either[Vector[RefResolverIssue], LoadedModel] = {
    result match {
      case ModelParsingResult.Success(_, model) if model.includes.isEmpty =>
        Right(LoadedModel(model.definitions))

      case ModelParsingResult.Success(path, model) if model.includes.nonEmpty =>
        Left(Vector(RefResolverIssue.InclusionsInOverlay(forDomain, path, model.includes)))

      case f: ModelParsingResult.Failure =>
        Left(Vector(RefResolverIssue.UnparseableInclusion(forDomain, List.empty, f)))
    }
  }

  private def resolveIncludes(domainPath: FSPath, parsed: ParsedDomain): Either[Vector[RefResolverIssue], ParsedDomain] = {
    val m = parsed.model
    val domainId = parsed.decls.id
    val domainOverlay = findOverlay(domainPath).map(resolveOverlay(domainId))

    val allIncludes = m.includes
      .map(i => loadModel(domainId, i, Seq(i)))
    val withOverlay = allIncludes ++ domainOverlay.toSeq

    val x = for {
      model <- merge(m, withOverlay)
    } yield {
      parsed.copy(model = parsed.model.copy(definitions = model.definitions.toList, includes = Seq.empty))
    }
    x
  }

  private def loadModel(forDomain: DomainId, includePath: Inclusion, stack: Seq[Inclusion]): Either[Vector[RefResolverIssue], LoadedModel] = {
    findModel(forDomain, includePath)
      .map {
        case ModelParsingResult.Success(modelPath, model) =>
          val modelOverlay = findOverlay(modelPath).map(resolveOverlay(forDomain))

          val subincludes = model.includes
            .map(i => loadModel(forDomain, i, stack :+ i))

          merge(model, subincludes ++ modelOverlay.toSeq)

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
    val issues = subincludes.collect { case Left(subissues) => subissues }.flatten.toVector

    if (issues.nonEmpty) {
      Left(issues)
    } else {
      val submodels = subincludes.collect { case Right(m) => m }
      val folded = submodels.fold(LoadedModel(model.definitions)) {
        case (acc, m) => acc ++ m
      }
      Right(folded)
    }
  }

  private def findOverlay(forFile: FSPath): Option[ModelParsingResult] = {
    val candidates = Set(forFile.move(p => ModelLoader.overlayVirtualDir +: p))
    domains.overlays.results.find(f => candidates.contains(f.path))
  }

  private def findModel(forDomain: DomainId, includePath: Inclusion): Option[ModelParsingResult] = {
    val includeparts = includePath.i.split('/')
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
