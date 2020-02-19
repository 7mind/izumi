package izumi.fundamentals.reflection.test

import izumi.fundamentals.reflection.macrortti.{LTag, LightTypeTag}

object PlatformSpecific {
  def fromRuntime[T: LTag]: LightTypeTag = LTag[T].tag
}
