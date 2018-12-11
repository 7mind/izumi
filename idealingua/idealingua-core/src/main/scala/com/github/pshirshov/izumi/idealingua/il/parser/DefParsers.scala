package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.domains.ParsedDomain
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.ParsedModel
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.models.ModelMember
import fastparse.NoWhitespace._
import fastparse._

class DefParsers(context: IDLParserContext) {

  import com.github.pshirshov.izumi.idealingua.il.parser.structure.sep._
  import context._

  protected[parser] def modelDef[_: P]: P[ParsedModel] = P(any ~ defMember.anyMember.rep(sep = any) ~ any ~ End)
    .map {
      defs =>
        val tlds = defs.collect({case ModelMember.MMTopLevelDefn(defn) => defn})
        val inclusions = defs.collect({case ModelMember.MMInclusion(inc) => inc})

        ParsedModel(tlds, inclusions)
    }

  protected[parser] def fullDomainDef[_: P]: P[ParsedDomain] = P(Start ~ any ~ defDomain.decl ~ modelDef).map {
    case (decl, defs) =>
      ParsedDomain(decl, defs)
  }

}
