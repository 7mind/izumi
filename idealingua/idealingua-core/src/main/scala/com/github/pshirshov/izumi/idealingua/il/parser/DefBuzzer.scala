package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL.ILBuzzer
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw._
import fastparse.all._


trait DefBuzzer {

  import sep._

  final val method = DefSignature.signature(kw.defe).map(DefSignature.toSignature)

  // other method kinds should be added here
  final val methods: Parser[Seq[RawMethod]] = P(method.rep(sep = any))

  final val buzzerBlock = aggregates.cblock(kw.buzzer, methods)
    .map {
      case (c, i, v) => ILBuzzer(Buzzer(i.toBuzzerId, v.toList, c))
    }

}

object DefBuzzer extends DefBuzzer {
}




