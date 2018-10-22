package com.github.pshirshov.izumi.fundamentals.reflection

import scala.language.experimental.macros

final case class HKTagMaterializer[DIU <: WithTags with Singleton, T](value: DIU#HKTag[T]) extends AnyVal

object HKTagMaterializer {

  implicit def materialize[DIU <: WithTags with Singleton, T]: HKTagMaterializer[DIU, T] = macro TagMacro.fixupHKTagArgStruct[DIU, T]

}
