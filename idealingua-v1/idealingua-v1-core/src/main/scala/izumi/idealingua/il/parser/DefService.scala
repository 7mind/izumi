package izumi.idealingua.il.parser

import izumi.idealingua.il.parser.structure._
import izumi.idealingua.model.il.ast.raw.defns.{RawMethod, RawService, RawTopLevelDefn}
import fastparse.NoWhitespace._
import fastparse._


class DefService(context: IDLParserContext) {

  import context._
  import sep._

  // other method kinds should be added here
  def methods[_: P]: P[Seq[RawMethod]] = P(defSignature.method(kw.defm).rep(sep = any))

  def serviceBlock[_: P]: P[RawTopLevelDefn.TLDService] = P(metaAgg.cblock(kw.service, methods))
    .map {
      case (c, i, v) => RawService(i.toServiceId, v.toList, c)
    }
    .map(RawTopLevelDefn.TLDService)

}
