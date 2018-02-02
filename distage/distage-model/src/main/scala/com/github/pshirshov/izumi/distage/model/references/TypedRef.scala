package com.github.pshirshov.izumi.distage.model.references

import com.github.pshirshov.izumi.fundamentals.reflection.RuntimeUniverse

case class TypedRef[+T: RuntimeUniverse.Tag](value: T) {
  def symbol: RuntimeUniverse.TypeFull = RuntimeUniverse.SafeType.get[T]
}
