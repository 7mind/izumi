package com.github.pshirshov.izumi.distage.model.reflection.universe

import com.github.pshirshov.izumi.fundamentals.reflection.{SafeType0, WithTags}

trait WithDISafeType {
  this: DIUniverseBase with WithTags =>

  // TODO: hotspot, hashcode on keys is inefficient
  case class SafeType(override val tpe: TypeNative) extends SafeType0[u.type](u, tpe)

  object SafeType {
    def get[T: Tag]: SafeType = SafeType(Tag[T].tag.tpe)
    def getK[K[_]: TagK]: SafeType = SafeType(TagK[K].tag.tpe)

    def unsafeGetWeak[T: WeakTag]: SafeType = SafeType(WeakTag[T].tag.tpe)

    implicit class SafeTypeUnsafeToTag(tpe: SafeType) {
      def unsafeToTag[T]: Tag[T] = Tag.unsafeFromType[T](tpe.tpe)
    }
  }

}
