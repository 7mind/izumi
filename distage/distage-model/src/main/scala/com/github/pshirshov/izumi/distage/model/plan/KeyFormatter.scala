package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

trait KeyFormatter {
  def formatKey(key: DIKey): String
}

object KeyFormatter {

  object Full extends KeyFormatter {
    override def formatKey(key: DIKey): String = key.toString
  }

}

trait TypeFormatter {
  final def formatType(key: SafeType): String = formatType(key.tpe)
  protected[this] def formatType(key: TypeNative): String
}

object TypeFormatter {

  object Full extends TypeFormatter {
    override protected[this] def formatType(key: RuntimeDIUniverse.TypeNative): String = key.toString
  }

}
