package org.bitbucket.pshirshov.izumi

import org.bitbucket.pshirshov.izumi.di.model.EqualitySafeType

import scala.reflect.runtime.universe
import scala.reflect.runtime.universe._

package object di {
  type Tag[T] = TypeTag[T]
  type TypeFull = EqualitySafeType
  type TypeNative = universe.Type
  type TypeSymb = universe.Symbol
  type CustomDef = Map[String, Any]
}
