package izumi.fundamentals.collections

package object nonempty {
  type NEList[+T] = NonEmptyList[T]
  type NESet[T] = NonEmptySet[T]
  type NEMap[K, +V] = NonEmptyMap[K, V]
  type NEString = NonEmptyString

  final val NEList = NonEmptyList
  final val NESet = NonEmptySet
  final val NEMap = NonEmptyMap
  final val NEString = NonEmptyString
}
