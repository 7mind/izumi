package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL._
import fastparse.all._


trait DefMember extends Aggregates {
  final val inclusion = kw(kw.include, sym.String)
    .map(v => ILInclude(v))





  final val anyMember: Parser[Val] = DefStructure.enumBlock |
    DefStructure.adtBlock |
    DefStructure.aliasBlock |
    DefStructure.cloneBlock |
    DefStructure.idBlock |
    DefStructure.mixinBlock |
    DefStructure.dtoBlock |
    DefService.serviceBlock |
    DefBuzzer.buzzerBlock |
    DefStreams.streamsBlock |
    DefConst.constBlock |
    inclusion

}

object DefMember extends DefMember {
}
