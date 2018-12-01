package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL._
import fastparse._


trait DefMember extends Aggregates {
  def inclusion[_:P]: P[ILInclude] = kw(kw.include, sym.String)
    .map(v => ILInclude(v))


  def anyMember[_:P]: P[Val] = DefStructure.enumBlock |
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
