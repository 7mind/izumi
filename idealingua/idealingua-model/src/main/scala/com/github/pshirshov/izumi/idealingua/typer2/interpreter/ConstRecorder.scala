package com.github.pshirshov.izumi.idealingua.typer2.interpreter

import com.github.pshirshov.izumi.idealingua.model.il.ast.InputPosition
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawVal.CMap
import com.github.pshirshov.izumi.idealingua.typer2.model.TypedConstId

trait ConstRecorder {
  def nextIndex(): Int


  def registerConst(id: TypedConstId, value: CMap, position: InputPosition): Unit
}
