package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL.ILBuzzer
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw._
import fastparse.NoWhitespace._
import fastparse._

class DefBuzzer(context: IDLParserContext) {

  import sep._
  import context._

  // other method kinds should be added here
  def methods[_: P]: P[Seq[RawMethod]] = P(defSignature.method(kw.defe).rep(sep = any))

  def buzzerBlock[_: P]: P[ILBuzzer] = metaAgg.cblock(kw.buzzer, methods)
    .map {
      case (c, i, v) => ILBuzzer(Buzzer(i.toBuzzerId, v.toList, c))
    }

}


