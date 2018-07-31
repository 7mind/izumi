package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.model.{ParsedDomain, ParsedModel}
import fastparse.all._


trait IDLParser {

  import com.github.pshirshov.izumi.idealingua.il.parser.structure.sep._

  def parseDomain(input: String): Parsed[ParsedDomain] = fullDomainDef.parse(input)

  def parseModel(input: String): Parsed[ParsedModel] = modelDef.parse(input)

  protected[parser] final val modelDef = P(any ~ DefMember.anyMember.rep(sep = any) ~ any ~ End).map {
    defs =>
      ParsedModel(defs)
  }

  protected[parser] final val fullDomainDef = P(any ~ DefDomain.decl ~ modelDef).map {
    case (did, imports, defs) =>
      println(defs)
      ParsedDomain(did, imports, defs)
  }

}

object IDLParser extends IDLParser {

}
