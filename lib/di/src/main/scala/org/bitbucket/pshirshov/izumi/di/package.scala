package org.bitbucket.pshirshov.izumi

import scala.reflect.runtime.universe
import scala.reflect.runtime.universe._

package object di {
  type Tag[T] = TypeTag[T]
  type TypeFull = universe.Type
  type TypeSymb = universe.Symbol
  type CustomDef = Map[String, AnyRef]
}
