package izumi.fundamentals.platform.language.types

object HigherKindedAny {
  type AnyF[_] = Any
  type AnyF2[+E, +A] = Any
}
