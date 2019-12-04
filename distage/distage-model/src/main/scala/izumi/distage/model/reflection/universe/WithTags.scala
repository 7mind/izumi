package izumi.distage.model.reflection.universe

import izumi.fundamentals.reflection.Tags

trait WithTags { self: DIUniverseBase =>
  final type Tag[T] = Tags.Tag[T]
  final val Tag = Tags.Tag

  final type HKTag[T] = Tags.HKTag[T]
  final val HKTag = Tags.HKTag

  final type WeakTag[T] = Tags.WeakTag[T]
  final val WeakTag = Tags.WeakTag

  final type TagK[K[_]] = Tags.TagK[K]
  final val TagK = Tags.TagK
  final type TagKK[K[_, _]] = Tags.TagKK[K]
  final val TagKK = Tags.TagKK
  final type TagK3[K[_, _, _]] = Tags.TagK3[K]
  final val TagK3 = Tags.TagK3

  final type TagT[K[_[_]]] = Tags.TagT[K]
  final val TagT = Tags.TagT
  final type TagTK[K[_[_], _]] = Tags.TagTK[K]
  final val TagTK = Tags.TagTK
  final type TagTKK[K[_[_], _, _]] = Tags.TagTKK[K]
  final val TagTKK = Tags.TagTKK
  final type TagTK3[K[_[_], _, _, _]] = Tags.TagTK3[K]
  final val TagTK3 = Tags.TagTK3
}
