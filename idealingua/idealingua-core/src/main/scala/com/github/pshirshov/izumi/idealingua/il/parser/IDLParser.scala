package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.model.loader.FSPath
import com.github.pshirshov.izumi.idealingua.model.parser
import com.github.pshirshov.izumi.idealingua.model.parser.{ParsedDomain, ParsedModel}
import fastparse.NoWhitespace._
import fastparse._

case class IDLParserContext(
                           file: FSPath
                           )

class IDLParserDefs(context: IDLParserContext) {
  import com.github.pshirshov.izumi.idealingua.il.parser.structure.sep._

  protected[parser] val defMember = new DefMember(context)
  protected[parser] val defDomain = new DefDomain(context)

  protected[parser] def modelDef[_:P]: P[ParsedModel] = P(any ~ defMember.anyMember.rep(sep = any) ~ any ~ End).map {
    defs =>
      ParsedModel(defs)
  }

  protected[parser] def fullDomainDef[_:P]: P[ParsedDomain] = P(any ~ defDomain.decl ~ modelDef).map {
    case (did, imports, defs) =>
      parser.ParsedDomain(did, imports, defs)
  }

}

class IDLParser(context: IDLParserContext) {
  def parseDomain(input: String): Parsed[ParsedDomain] = {
    val defs = new IDLParserDefs(context)
    parse(input, defs.fullDomainDef(_))
  }

  def parseModel(input: String): Parsed[ParsedModel] = {
    val defs = new IDLParserDefs(context)

    parse(input, defs.modelDef(_))
  }


}
