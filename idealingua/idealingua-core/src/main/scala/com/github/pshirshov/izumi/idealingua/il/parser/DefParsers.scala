package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.{ParsedDomain, ParsedModel}
import fastparse.NoWhitespace._
import fastparse._

class DefParsers(context: IDLParserContext) {
  import com.github.pshirshov.izumi.idealingua.il.parser.structure.sep._
  import context._

  protected[parser] def modelDef[_:P]: P[ParsedModel] = P(any ~ defMember.anyMember.rep(sep = any) ~ any ~ End).map {
    defs =>
      ParsedModel(defs)
  }

  protected[parser] def fullDomainDef[_:P]: P[ParsedDomain] = P(any ~ defDomain.decl ~ modelDef).map {
    case (decl, defs) =>
      ParsedDomain(decl, defs)
  }

}
