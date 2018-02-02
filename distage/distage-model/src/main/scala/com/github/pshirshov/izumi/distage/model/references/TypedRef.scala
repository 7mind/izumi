package com.github.pshirshov.izumi.distage.model.references

import com.github.pshirshov.izumi.fundamentals.reflection.{EqualitySafeType, RuntimeUniverse}

case class TypedRef[+T: RuntimeUniverse.Tag](value: T) {
  def symbol: RuntimeUniverse.TypeFull = EqualitySafeType.get[T]
}
