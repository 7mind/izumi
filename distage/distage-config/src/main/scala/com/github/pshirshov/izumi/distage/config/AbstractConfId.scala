package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

sealed trait AbstractConfId

final case class AutoConfId(context: DIKey) extends AbstractConfId {
  override def toString: String = s"auto[${context.toString}]"
}

final case class ConfId(context: String) extends AbstractConfId {
  override def toString: String = s"ctx[${context.toString}]"
}
