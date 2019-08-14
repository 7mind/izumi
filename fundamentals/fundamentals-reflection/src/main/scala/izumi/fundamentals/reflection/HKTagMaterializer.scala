package izumi.fundamentals.reflection

import scala.language.experimental.macros

/**
  * Workaround for a scalac bug whereby it loses the correct type of HKTag argument
  * Here, if implicit resolution fails because scalac thinks that `ArgStruct` is a WeakType,
  * we just inspect it and recreate HKTag Arg again.
  *
  * See: TagTest, "scalac bug: can't find HKTag when obscured by type lambda"
  *
  * TODO: report scalac bug
  */
final case class HKTagMaterializer[DIU <: WithTags with Singleton, T](value: DIU#HKTag[T]) extends AnyVal

object HKTagMaterializer {
  implicit def materialize[DIU <: WithTags with Singleton, T]: HKTagMaterializer[DIU, T] = macro TagMacro.fixupHKTagArgStruct[DIU, T]
}
