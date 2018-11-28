package com.github.pshirshov.izumi.idealingua.il.loader

import com.github.pshirshov.izumi.idealingua.il.loader.model.FSPath
import com.github.pshirshov.izumi.idealingua.il.parser.model.{ParsedDomain, ParsedModel}

sealed trait DomainParsingResult {
  def path: FSPath
}

object DomainParsingResult {
  final case class Success(path: FSPath, domain: ParsedDomain) extends DomainParsingResult
  final case class Failure(path: FSPath, message: String) extends DomainParsingResult
}

sealed trait ModelParsingResult {
  def path: FSPath
}

object ModelParsingResult {
  final case class Success(path: FSPath, model: ParsedModel) extends ModelParsingResult
  final case class Failure(path: FSPath, message: String) extends ModelParsingResult
}

case class ParsedDomains(results: Seq[DomainParsingResult])

case class ParsedModels(results: Seq[ModelParsingResult])


trait ModelParser {
  def parseModels(files: Map[FSPath, String]): ParsedModels

  def parseDomains(files: Map[FSPath, String]): ParsedDomains
}
