package com.github.pshirshov.izumi.distage.commons

import java.lang.reflect.Method

import com.github.pshirshov.izumi.distage.model.reflection.universe.MirrorProvider
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.u._

class UnboxingTool(mirrorProvider: MirrorProvider) {

  def unbox(info: DIKey, value: Any): AnyRef = {
    val tpe = info.tpe.tpe
    if (tpe.typeSymbol.isClass && tpe.typeSymbol.asClass.isDerivedValueClass) {
      val u = getUnboxMethod(tpe)
      u.invoke(value)
    } else {
      value.asInstanceOf[AnyRef]
    }
  }

  protected def getUnboxMethod(info: Type): Method = {
    val symbol = info.typeSymbol.asType
    val fields@(field :: _) = symbol.toType.decls.collect { case ts: TermSymbol if ts.isParamAccessor && ts.isMethod => ts }.toList
    assert(fields.length == 1, s"$symbol: $fields")
    mirrorProvider.mirror.runtimeClass(symbol.asClass).getDeclaredMethod(field.name.toString)
  }
}
