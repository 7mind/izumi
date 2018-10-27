package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL.ILBuzzer
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw._
import fastparse.NoWhitespace._
import fastparse._

trait DefBuzzer {

  import sep._

  final def method[_:P] = DefSignature.signature(kw.defe).map(DefSignature.toSignature)

  // other method kinds should be added here
  final def methods[_:P]: P[Seq[RawMethod]] = P(method.rep(sep = any))

  final def buzzerBlock[_:P] = aggregates.cblock(kw.buzzer, methods)
    .map {
      case (c, i, v) => ILBuzzer(Buzzer(i.toBuzzerId, v.toList, c))
    }

}

object DefBuzzer extends DefBuzzer {
}




