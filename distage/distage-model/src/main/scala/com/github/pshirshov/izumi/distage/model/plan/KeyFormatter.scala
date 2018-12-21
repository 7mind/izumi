package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

trait KeyFormatter {
  def format(key: DIKey): String
}

object KeyFormatter {

  object Full extends KeyFormatter {
    override def format(key: DIKey): String = key.toString
  }

}

trait TypeFormatter {
  def format(key: TypeNative): String
  def format(key: SafeType): String = format(key.tpe)
}

object TypeFormatter {

  object Full extends TypeFormatter {
    override def format(key: RuntimeDIUniverse.TypeNative): String = key.toString
  }

}
