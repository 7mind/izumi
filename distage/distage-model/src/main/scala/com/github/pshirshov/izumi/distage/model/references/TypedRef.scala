package com.github.pshirshov.izumi.distage.model.references

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse

case class TypedRef[+T: RuntimeUniverse.Tag](value: T) {
  def symbol: RuntimeUniverse.TypeFull = RuntimeUniverse.SafeType.get[T]
}
