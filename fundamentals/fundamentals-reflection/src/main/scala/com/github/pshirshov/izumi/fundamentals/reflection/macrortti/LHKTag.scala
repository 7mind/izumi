package com.github.pshirshov.izumi.fundamentals.reflection.macrortti

import scala.language.experimental.macros

final case class LHKTag[T](fullLightTypeTag: FLTT)

object LHKTag {
  implicit def materialize[T]: LHKTag[T] = macro LightTypeTagImpl.makeHKTag[T]
}
