package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawMethod
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTypeDef.RawBuzzer
import fastparse.NoWhitespace._
import fastparse._

class DefBuzzer(context: IDLParserContext) {

  import context._
  import sep._

  def buzzerBlock[_: P]: P[RawBuzzer] = P(metaAgg.cblock(kw.buzzer, methods))
    .map {
      case (c, i, v) => RawBuzzer(i, v.toList, c)
    }

  // other method kinds should be added here
  private def methods[_: P]: P[Seq[RawMethod]] = P(defSignature.method(kw.defe).rep(sep = any))
}


