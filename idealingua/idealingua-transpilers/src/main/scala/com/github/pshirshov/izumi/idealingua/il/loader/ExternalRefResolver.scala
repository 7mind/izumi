package com.github.pshirshov.izumi.idealingua.il.loader

import java.nio.file.{Path, Paths}

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.CompletelyLoadedDomain
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL.ILImport
import com.github.pshirshov.izumi.idealingua.model.loader._

private[loader] class ExternalRefResolver(domains: UnresolvedDomains, domainExt: String) {
  // we need mutable state to handle cyclic references (even though they aren't supported by go we still handle them)
  private val processed = scala.collection.mutable.HashMap[DomainId, () => Either[LoadedDomain.ParsingFailed, CompletelyLoadedDomain]]()

  def resolveReferences(domain: DomainParsingResult): Either[LoadedDomain.ParsingFailed, CompletelyLoadedDomain] = {
    domain match {
      case DomainParsingResult.Success(path, parsed) =>
        val allIncludes = parsed.model.includes
          .map(includePath => findModel(domains, includePath))
          .map(_.get)
          .map {
            case ModelParsingResult.Success(_, model) =>
              LoadedModel(model.definitions)
            case ModelParsingResult.Failure(p, message) =>
              throw new IDLException(s"Can't find reference $p while handling ${parsed.did}")

          }
          .fold(LoadedModel(parsed.model.definitions))(_ ++ _)

        val importOps = parsed.imports.flatMap {
          i =>
            i.identifiers.map(ILImport(i.id, _))
        }

        if (parsed.imports.nonEmpty) {
          println(s"Resolving imports in ${domain.path}: ${parsed.imports}")
        }
        val imports = parsed.imports.map {
          imprt =>
            val d = findDomain(domains, toPath(imprt.id).toString)
            processed.getOrElseUpdate(imprt.id, {
              println(s"adding ${d.get.path} reader for ${domain.path}, have ${processed.size} readers")

              () => resolveReferences(d.get)
            })
        }
          .map {
            v =>
              v.apply() match {
                case Left(value) =>
                  ???
                case Right(value) =>
                  value.id -> value
              }

          }
          .toMap

        val loaded = CompletelyLoadedDomain(parsed.did, allIncludes.definitions ++ importOps, imports, path)
        Right(loaded)

      case DomainParsingResult.Failure(path, message) =>
        Left(LoadedDomain.ParsingFailed(path, message))
    }
  }

  private def toPath(id: DomainId): Path = {
    val p = Paths.get(id.toPackage.mkString("/"))
    p.getParent.resolve(s"${p.getFileName.toString}$domainExt")
  }

  private def findModel(domains: UnresolvedDomains, includePath: String): Option[ModelParsingResult] = {
    domains.models.results.find(_.path == FSPath(Paths.get(includePath)))
  }

  private def findDomain(domains: UnresolvedDomains, includePath: String): Option[DomainParsingResult] = {
    domains.domains.results.find(_.path == FSPath(Paths.get(includePath)))
  }

}
