package izumi.idealingua.model.loader

import izumi.idealingua.model.il.ast.raw.domains.ParsedDomain

sealed trait DomainParsingResult {
  def path: FSPath
}

object DomainParsingResult {
  final case class Success(path: FSPath, domain: ParsedDomain) extends DomainParsingResult
  final case class Failure(path: FSPath, message: String) extends DomainParsingResult
}

case class ParsedDomains(results: Seq[DomainParsingResult])
