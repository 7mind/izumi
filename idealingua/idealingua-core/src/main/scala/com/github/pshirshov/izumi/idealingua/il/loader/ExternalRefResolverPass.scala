package com.github.pshirshov.izumi.idealingua.il.loader

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL.ILImport
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.{CompletelyLoadedDomain, IL}
import com.github.pshirshov.izumi.idealingua.model.loader._
import com.github.pshirshov.izumi.idealingua.model.parser.ParsedDomain
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

import scala.collection.mutable

private[loader] class ExternalRefResolverPass(domains: UnresolvedDomains, domainExt: String) {
  // we need mutable state to handle cyclic references (even though they aren't supported by go we still handle them)
  private val processed = mutable.HashMap[DomainId, CompletelyLoadedDomain]()

  def resolveReferences(domain: DomainParsingResult): Either[LoadedDomain.Failure, CompletelyLoadedDomain] = {
    domain match {
      case DomainParsingResult.Success(path, parsed) =>
        handleSuccess(path, parsed)

      case DomainParsingResult.Failure(path, message) =>
        Left(LoadedDomain.ParsingFailed(path, message))
    }
  }

  private def handleSuccess(path: FSPath, parsed: ParsedDomain): Either[Nothing, CompletelyLoadedDomain] = {
    val withIncludes = resolveIncludes(parsed)
    val loaded = new CompletelyLoadedDomainMutable(parsed.did, withIncludes, path, processed, parsed.imports.map(_.id).toSet)

    processed.update(parsed.did, loaded)

    parsed.imports.filterNot(i => processed.contains(i.id)).foreach {
      imprt =>
        val d = findDomain(domains, toPath(imprt.id))
        resolveReferences(d.get)
    }

    Right(loaded)
  }

  private def loadModel(forDomain: DomainId, includePath: String, stack: Seq[String]): LoadedModel = {
    findModel(forDomain, domains, includePath)
      .map {
        case ModelParsingResult.Success(_, model) =>

          model.includes
            .map(i => loadModel(forDomain, i, stack :+ i))
            .fold(LoadedModel(model.definitions)) {
              case (acc, m) => acc ++ m
            }

        case ModelParsingResult.Failure(path, message) =>
          throw new IDLException(s"$forDomain: can't parse inclusion $path, inclusion chain: $forDomain->${stack.mkString("->")}. Message: $message")

      }
      .getOrElse(throw new IDLException(s"$forDomain: can't find inclusion $includePath, inclusion chain: $forDomain->${stack.mkString("->")}. Available: ${domains.models.results.map {
        case ModelParsingResult.Success(path, _) =>
          s"$path: OK"
        case ModelParsingResult.Failure(path, message) =>
          s"$path: KO=$message"
      }.niceList()}"))
  }

  private def resolveIncludes(parsed: ParsedDomain): Seq[IL.Val] = {
    val m = parsed.model
    val allIncludes = m.includes
      .map(i => loadModel(parsed.did, i, Seq.empty))
      .fold(LoadedModel(parsed.model.definitions))(_ ++ _)

    val importOps = parsed.imports.flatMap {
      i =>
        i.identifiers.map(ILImport(i.id, _))
    }

    val withIncludes = allIncludes.definitions ++ importOps
    withIncludes
  }

  private def toPath(id: DomainId): FSPath = {
    val pkg = id.toPackage
    FSPath(pkg.init :+ s"${pkg.last}$domainExt")
  }

  private def findModel(forDomain: DomainId, domains: UnresolvedDomains, includePath: String): Option[ModelParsingResult] = {
    val absolute = FSPath(includePath)
    val relative = FSPath(forDomain.toPackage.init ++ includePath.split("/"))
    println(s"? $absolute, $relative")
    domains.models.results.find(f => f.path == absolute || f.path == relative)
  }

  private def findDomain(domains: UnresolvedDomains, includePath: FSPath): Option[DomainParsingResult] = {
    domains.domains.results.find(_.path == includePath)
  }

}
