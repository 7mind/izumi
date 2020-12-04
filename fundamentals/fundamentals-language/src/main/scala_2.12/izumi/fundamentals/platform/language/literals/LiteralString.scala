package izumi.fundamentals.platform.language.literals

import scala.language.implicitConversions

final class LiteralString(private val value: String) extends AnyVal {
  type T <: String
}

object LiteralString {
  type Of[T0 <: String] = LiteralString { type T = T0 }
  type Get[L <: LiteralString] = L#T

  @inline implicit final def apply(s: String): LiteralString { type T = s.type } = null.asInstanceOf[LiteralString { type T = s.type }]
  @inline implicit final def unwrap[L <: LiteralString](literalString: L): L#T = literalString.value.asInstanceOf[L#T]
}
