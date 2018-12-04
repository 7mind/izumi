package com.github.pshirshov.izumi.idealingua.il.loader

import com.github.pshirshov.izumi.idealingua.il.parser.IDLParser
import com.github.pshirshov.izumi.idealingua.model.loader._
import fastparse._


class ModelParserImpl() extends ModelParser {
  def parseModels(files: Map[FSPath, String]): ParsedModels = ParsedModels {
    files
      .mapValues(IDLParser.parseModel)
      .toSeq
      .map {
        case (p, Parsed.Success(value, _)) =>
          ModelParsingResult.Success(p, value)

        case (p, f@Parsed.Failure(_, _, _)) =>
          ModelParsingResult.Failure(p, s"Failed to parse model $p: ${f.msg}")

      }
  }

  def parseDomains(files: Map[FSPath, String]): ParsedDomains = ParsedDomains {
    files
      .mapValues(IDLParser.parseDomain)
      .toSeq
      .map {
        case (p, Parsed.Success(value, _)) =>
          DomainParsingResult.Success(p, value)

        case (p, f@Parsed.Failure(_, _, _)) =>
          DomainParsingResult.Failure(p, s"Failed to parse domain $p: ${f.trace().msg}")
      }
  }
}
