package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.model.parser
import com.github.pshirshov.izumi.idealingua.model.parser.{ParsedDomain, ParsedModel}
import fastparse._
import fastparse.NoWhitespace._

trait IDLParser {

  import com.github.pshirshov.izumi.idealingua.il.parser.structure.sep._

  def parseDomain(input: String): Parsed[ParsedDomain] = parse(input, fullDomainDef(_))

  def parseModel(input: String): Parsed[ParsedModel] = parse(input, modelDef(_))

  protected[parser] def modelDef[_:P]: P[ParsedModel] = P(any ~ DefMember.anyMember.rep(sep = any) ~ any ~ End).map {
    defs =>
      ParsedModel(defs)
  }

  protected[parser] def fullDomainDef[_:P]: P[ParsedDomain] = P(any ~ DefDomain.decl ~ modelDef).map {
    case (did, imports, defs) =>
      parser.ParsedDomain(did, imports, defs)
  }

}

object IDLParser extends IDLParser {

}
