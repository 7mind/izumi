package com.github.pshirshov.izumi.distage.config.annotations

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

sealed trait AbstractConfId

final case class AutoConfId(binding: DIKey, parameter: Association.Parameter) extends AbstractConfId {
  override def toString: String = s"auto[binding: ${binding.toString}, parameter: $parameter]"
}

final case class ConfId(binding: DIKey, parameter: Association.Parameter, nameOverride: String) extends AbstractConfId {
  override def toString: String = s"ctx[binding:${binding.toString}, parameter: $parameter, ctxname:$nameOverride]"
}
