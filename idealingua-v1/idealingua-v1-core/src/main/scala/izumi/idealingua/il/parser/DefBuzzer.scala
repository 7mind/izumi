package izumi.idealingua.il.parser

import izumi.idealingua.il.parser.structure._
import izumi.idealingua.model.il.ast.raw.defns.{RawBuzzer, RawMethod, RawTopLevelDefn}
import fastparse.NoWhitespace._
import fastparse._

class DefBuzzer(context: IDLParserContext) {

  import context._
  import sep._

  // other method kinds should be added here
  def methods[_: P]: P[Seq[RawMethod]] = P(defSignature.method(kw.defe).rep(sep = any))

  def buzzerBlock[_: P]: P[RawTopLevelDefn.TLDBuzzer] = P(metaAgg.cblock(kw.buzzer, methods))
    .map {
      case (c, i, v) => RawBuzzer(i.toBuzzerId, v.toList, c)
    }
    .map(RawTopLevelDefn.TLDBuzzer)

}


