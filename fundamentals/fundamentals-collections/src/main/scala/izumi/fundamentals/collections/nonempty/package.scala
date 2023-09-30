package izumi.fundamentals.collections

package object nonempty {
  @deprecated("NonEmptyList had been renamed to NEList")
  type NonEmptyList[+T] = NEList[T]

  @deprecated("NonEmptySet had been renamed to NESet")
  type NonEmptySet[T] = NESet[T]

  @deprecated("NonEmptyMap had been renamed to NEMap")
  type NonEmptyMap[K, +V] = NEMap[K, V]

  @deprecated("NonEmptyString had been renamed to NEString")
  type NonEmptyString = NEString

  @deprecated("NonEmptyList had been renamed to NEList")
  final val NonEmptyList = NEList

  @deprecated("NonEmptySet had been renamed to NESet")
  final val NonEmptySet = NESet

  @deprecated("NonEmptyMap had been renamed to NEMap")
  final val NonEmptyMap = NEMap

  @deprecated("NonEmptyString had been renamed to NEString")
  final val NonEmptyString = NEString
}
