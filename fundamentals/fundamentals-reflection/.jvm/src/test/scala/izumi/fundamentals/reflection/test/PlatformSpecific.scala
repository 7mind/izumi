package izumi.fundamentals.reflection.test

import izumi.fundamentals.reflection.macrortti.{LightTypeTag, LightTypeTagImpl}
import scala.reflect.runtime.{universe => ru}

object PlatformSpecific {
  def fromRuntime[T: ru.TypeTag]: LightTypeTag = LightTypeTagImpl.makeLightTypeTag(ru)(ru.typeOf[T])
}
