package com.github.pshirshov.izumi.idealingua.typer2.interpreter

import com.github.pshirshov.izumi.idealingua.model.il.ast.InputPosition
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawVal
import com.github.pshirshov.izumi.idealingua.typer2.model.TypedConstId

trait ConstRecorder {
  def nextIndex(): Int


  def registerConst(id: TypedConstId, value: RawVal, position: InputPosition): Unit
}
