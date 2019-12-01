package izumi.idealingua.il.parser

import izumi.idealingua.model.il.ast.raw.domains.ParsedDomain
import izumi.idealingua.model.il.ast.raw.models.{ModelMember, ParsedModel}
import fastparse.NoWhitespace._
import fastparse._

class DefParsers(context: IDLParserContext) {

  import izumi.idealingua.il.parser.structure.sep._
  import context._

  protected[parser] def modelDef[_: P]: P[ParsedModel] = P(any ~ defMember.anyMember.rep(sep = any) ~ any ~ End)
    .map {
      defs =>
        val tlds = defs.collect {case ModelMember.MMTopLevelDefn(defn) => defn}
        val inclusions = defs.collect {case ModelMember.MMInclusion(inc) => inc}

        ParsedModel(tlds, inclusions)
    }

  protected[parser] def fullDomainDef[_: P]: P[ParsedDomain] = P(Start ~ any ~ defDomain.decl ~ modelDef).map {
    case (decl, defs) =>
      ParsedDomain(decl, defs)
  }

}
