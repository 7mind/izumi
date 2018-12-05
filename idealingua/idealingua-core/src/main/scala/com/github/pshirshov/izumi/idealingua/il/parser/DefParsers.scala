package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.model.parser
import com.github.pshirshov.izumi.idealingua.model.parser.{ParsedDomain, ParsedModel}
import fastparse._
import fastparse.NoWhitespace._

class DefParsers(context: IDLParserContext) {
  import com.github.pshirshov.izumi.idealingua.il.parser.structure.sep._
  import context._

  protected[parser] def modelDef[_:P]: P[ParsedModel] = P(any ~ defMember.anyMember.rep(sep = any) ~ any ~ End).map {
    defs =>
      ParsedModel(defs)
  }

  protected[parser] def fullDomainDef[_:P]: P[ParsedDomain] = P(any ~ defDomain.decl ~ modelDef).map {
    case (did, imports, defs) =>
      parser.ParsedDomain(did, imports, defs)
  }

}
