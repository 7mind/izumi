package com.github.pshirshov.izumi.distage.model.references

import com.github.pshirshov.izumi.distage.model.reflection.universe.{DIUniverseBase, WithDISafeType}
import com.github.pshirshov.izumi.fundamentals.reflection.WithTags

trait WithDITypedRef {
  this: DIUniverseBase
    with WithDISafeType
    with WithTags =>

  case class TypedRef[+T](value: T, symbol: SafeType)

  object TypedRef {
    def apply[T: Tag](value: T): TypedRef[T] =
      TypedRef(value, SafeType.get[T])
  }

}
