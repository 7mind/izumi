package izumi.idealingua.il.loader

import izumi.idealingua.model.loader.{FSPath, ParsedDomains, ParsedModels}

trait ModelParser {
  def parseModels(files: Map[FSPath, String]): ParsedModels

  def parseDomains(files: Map[FSPath, String]): ParsedDomains
}
