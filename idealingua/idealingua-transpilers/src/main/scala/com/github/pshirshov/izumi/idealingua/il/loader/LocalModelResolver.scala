package com.github.pshirshov.izumi.idealingua.il.loader

import java.io.File
import java.nio.file.Path

import com.github.pshirshov.izumi.idealingua.model.il.ast.IDLTyper
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.DomainDefinitionParsed
import com.github.pshirshov.izumi.idealingua.model.loader.{DomainParsingResult, LoadedDomain, LoadedModels, UnresolvedDomains}
import com.github.pshirshov.izumi.idealingua.model.typespace.{TypespaceImpl, TypespaceVerifier}

class LocalModelResolver(root: Path, classpath: Seq[File], parser: ModelParser, domainExt: String) extends ModelResolver {

  override def resolve(domains: UnresolvedDomains): LoadedModels = LoadedModels {
    domains.domains.results.map {
      case DomainParsingResult.Success(path, parsed) =>
        val d = new LocalDomainProcessor(root, classpath, parsed, domains, parser, domainExt).postprocess(path)
        Right(d)

      case DomainParsingResult.Failure(path, message) =>
        Left(LoadedDomain.ParsingFailed(path, message))
    }.map {
      case Left(value) =>
        value
      case Right(d) =>
        val ts: TypespaceImpl = runTyper(d)

        val issues = new TypespaceVerifier(ts).verify().toList
        if (issues.isEmpty) {
          LoadedDomain.Success(d.origin, ts)
        } else {
          LoadedDomain.TypingFailed(d.origin, d.id, issues)
        }
    }
  }

  private def runTyper(d: DomainDefinitionParsed): TypespaceImpl = {
    val domain = new IDLTyper(d).perform()
    val ts = new TypespaceImpl(domain)
    ts
  }
}
