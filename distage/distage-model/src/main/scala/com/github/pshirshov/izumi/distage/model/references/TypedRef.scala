package com.github.pshirshov.izumi.distage.model.references

import com.github.pshirshov.izumi.fundamentals.reflection.{EqualitySafeType, Tag, TypeFull}

case class TypedRef[+T: Tag](value: T) {
  def symbol: TypeFull = EqualitySafeType.get[T]
}
