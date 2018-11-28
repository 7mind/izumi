package com.github.pshirshov.izumi.idealingua.il.loader

import java.io.File
import java.nio.file.Path

import com.github.pshirshov.izumi.idealingua.il.loader.model.{LoadedDomain, LoadedModels, UnresolvedDomains}
import com.github.pshirshov.izumi.idealingua.model.il.ast.IDLTyper
import com.github.pshirshov.izumi.idealingua.model.typespace.{TypespaceImpl, TypespaceVerifier}

class LocalModelResolver(root: Path, classpath: Seq[File], parser: ModelParser, domainExt: String) extends ModelResolver {

  override def resolve(domains: UnresolvedDomains): LoadedModels = model.LoadedModels {
    domains.domains.results.map {
      case DomainParsingResult.Success(path, parsed) =>
        val d = new LocalDomainProcessor(root, classpath, parsed, domains.domains, domains.models, parser, domainExt).postprocess()
        val domain = new IDLTyper(d).perform()

        val ts = new TypespaceImpl(domain)
        val issues = new TypespaceVerifier(ts).verify().toList
        if (issues.isEmpty) {
          LoadedDomain.Success(path, ts)
        } else {
          LoadedDomain.TypingFailed(path, d.id, issues)
        }

      case DomainParsingResult.Failure(path, message) =>
        LoadedDomain.ParsingFailed(path, message)

    }
  }
}
