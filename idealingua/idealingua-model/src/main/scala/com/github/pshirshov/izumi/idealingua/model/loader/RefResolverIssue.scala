package com.github.pshirshov.izumi.idealingua.model.loader

import com.github.pshirshov.izumi.idealingua.model.common.DomainId

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

sealed trait RefResolverIssue

object RefResolverIssue {
  final case class DuplicatedDomains(imported: DomainId, paths: List[FSPath]) extends RefResolverIssue {
    override def toString: String = s"$imported: multiple domains have the same identifier: ${paths.niceList()}"
  }

  final case class UnparseableInclusion(domain: DomainId,stack: List[String], failure: ModelParsingResult.Failure) extends RefResolverIssue {
    override def toString: String = s"$domain: can't parse inclusion ${failure.path}, inclusion chain: $domain->${stack.mkString("->")}. Message: ${failure.message}"
  }

  final case class MissingInclusion(domain: DomainId,stack: List[String], path: String, diagnostic: List[String]) extends RefResolverIssue {
    override def toString: String = s"$domain: can't find inclusion $path, inclusion chain: $domain->${stack.mkString("->")}. Available: ${diagnostic.niceList()}"
  }

  final case class UnresolvableImport(domain: DomainId, imported: DomainId, failure: LoadedDomain.Failure) extends RefResolverIssue {
    override def toString: String = s"$domain: can't resolve import $imported, problem: $failure"
  }

  final case class MissingImport(domain: DomainId, imported: DomainId, diagnostic: List[String]) extends RefResolverIssue {
    override def toString: String = s"$domain: can't find import $imported. Available: ${diagnostic.niceList()}"
  }
}
