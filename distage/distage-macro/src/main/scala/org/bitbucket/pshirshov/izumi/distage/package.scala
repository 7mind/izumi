package com.github.pshirshov.izumi

import com.github.pshirshov.izumi.distage.model.EqualitySafeType

import scala.reflect.runtime.universe
import scala.reflect.runtime.universe._

package object distage {
  type Tag[T] = TypeTag[T]
  type TypeFull = EqualitySafeType
  type TypeNative = universe.Type
  type TypeSymb = universe.Symbol
  type MethodSymb = universe.MethodSymbol
  type CustomDef = Map[String, Any]
}
