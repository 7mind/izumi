package com.github.pshirshov.izumi.distage.model.reflection.universe

import com.github.pshirshov.izumi.fundamentals.reflection.{SafeType0, WithTags}

trait WithDISafeType {
  this: DIUniverseBase with WithTags =>

  // TODO: hotspots, hashcode on keys is inefficient
  case class SafeType(tpe: TypeNative) extends SafeType0[u.type](tpe)

  object SafeType {
    def get[T: Tag]: SafeType = SafeType(Tag[T].tag.tpe)

    def unsafeGetWeak[T: WeakTag]: SafeType = SafeType(WeakTag[T].tag.tpe)
  }

}
