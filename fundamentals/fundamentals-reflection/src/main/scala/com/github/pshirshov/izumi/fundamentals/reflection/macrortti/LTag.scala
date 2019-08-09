package com.github.pshirshov.izumi.fundamentals.reflection.macrortti

import scala.language.experimental.macros

final case class LTag[T](fullLightTypeTag: FLTT)

object LTag {
  def apply[T: LTag]: LTag[T] = implicitly

  implicit def materialize[T]: LTag[T] = macro LightTypeTagMacro.makeTag[T]
}

final case class LWeakTag[T](fullLightTypeTag: FLTT)

object LWeakTag {
  def apply[T: LWeakTag]: LWeakTag[T] = implicitly

  implicit def materialize[T]: LWeakTag[T] = macro LightTypeTagMacro.makeWeakTag[T]
}
