package izumi.fundamentals.reflection

import scala.language.experimental.macros

final case class TagMaterializer[DIU <: WithTags with Singleton, T](value: DIU#Tag[T]) extends AnyVal

object TagMaterializer {
  implicit def materialize[DIU <: WithTags with Singleton, T]: TagMaterializer[DIU, T] = macro TagMacro.impl[DIU, T]
}
