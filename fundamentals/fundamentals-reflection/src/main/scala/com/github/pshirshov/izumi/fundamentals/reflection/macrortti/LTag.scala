package com.github.pshirshov.izumi.fundamentals.reflection.macrortti

import scala.language.experimental.macros

final case class LTag[T](fullLightTypeTag: FLTT)

object LTag {
  def apply[T: LTag]: LTag[T] = implicitly

  implicit def materialize[T]: LTag[T] = macro LightTypeTagImpl.makeTag[T]
}
