package izumi.fundamentals.platform.language.literals

object LiteralBoolean {
  type Of[T <: Boolean] = T
  type Get[T <: LiteralBoolean] = T

  def apply(b: Boolean): b.type = b

  @inline final def True: true = true
  @inline final def False: false = false
}
