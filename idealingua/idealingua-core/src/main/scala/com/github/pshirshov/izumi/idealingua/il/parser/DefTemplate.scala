package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn.TLDTemplate
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTypeDef
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTypeDef.Template
import fastparse.NoWhitespace._
import fastparse._

class DefTemplate(context: IDLParserContext) {

  import context._
  import defStructure._
  import defService._
  import defBuzzer._

  def templateBlock[_: P]: P[TLDTemplate] = P(metaAgg.kwWithMeta(kw.template, inline ~ ids.typeArgumentsShort ~/ (inline ~ templateValue)))
    .map {
      case (c, (a, v)) => TLDTemplate(Template(a.toList, v, c))
    }

  protected[parser] def templateValue[_: P]: P[RawTypeDef.WithTemplating] = adtBlock | dtoBlock | mixinBlock | serviceBlock | buzzerBlock
}


