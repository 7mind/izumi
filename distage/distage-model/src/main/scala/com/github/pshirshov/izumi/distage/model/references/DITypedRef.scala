package com.github.pshirshov.izumi.distage.model.references

import com.github.pshirshov.izumi.distage.model.reflection.universe.{DISafeType, DIUniverseBase}

trait DITypedRef {
  this: DIUniverseBase
    with DISafeType =>

  case class TypedRef[+T](value: T, symbol: TypeFull)

  object TypedRef {
    def apply[T: Tag](value: T): TypedRef[T] =
      TypedRef(value, SafeType.get[T])
  }

}
