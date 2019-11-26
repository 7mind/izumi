package izumi.distage.model.reflection.universe

import izumi.fundamentals.reflection.Tags

trait WithTags {
  self: DIUniverseBase =>
  val tags: (Tags { val u: self.u.type }) with Singleton

  final type Tag[T] = tags.Tag[T]
  final val Tag = tags.Tag

  final type HKTag[T] = tags.HKTag[T]
  final val HKTag = tags.HKTag

  final type WeakTag[T] = tags.WeakTag[T]
  final val WeakTag = tags.WeakTag

  final type TagK[K[_]] = tags.TagK[K]
  final val TagK = tags.TagK
  final type TagKK[K[_, _]] = tags.TagKK[K]
  final val TagKK = tags.TagKK
  final type TagK3[K[_, _, _]] = tags.TagK3[K]
  final val TagK3 = tags.TagK3

  final type TagT[K[_[_]]] = tags.TagT[K]
  final val TagT = tags.TagT
  final type TagTK[K[_[_], _]] = tags.TagTK[K]
  final val TagTK = tags.TagTK
  final type TagTKK[K[_[_], _, _]] = tags.TagTKK[K]
  final val TagTKK = tags.TagTKK
  final type TagTK3[K[_[_], _, _, _]] = tags.TagTK3[K]
  final val TagTK3 = tags.TagTK3
}
