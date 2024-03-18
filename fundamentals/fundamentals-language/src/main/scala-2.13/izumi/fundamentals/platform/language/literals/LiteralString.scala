package izumi.fundamentals.platform.language.literals

object LiteralString {
  type Of[T <: String] = T
  type Get[T <: LiteralString] = T

  def apply(s: String): s.type = s
}
