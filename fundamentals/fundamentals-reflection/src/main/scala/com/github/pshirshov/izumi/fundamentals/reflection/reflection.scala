package com.github.pshirshov.izumi.fundamentals

import com.sun.tools.javac.code.TypeTag

import scala.reflect.api.Universe
import scala.reflect.runtime.universe

package object reflection {
  //type TypeFull = RuntimeUniverse.SafeType

  type SingletonUniverse = Universe with scala.Singleton
  //type Tag[T] = RuntimeUniverse.Tag[T]
  //type TypeNative = RuntimeUniverse.TypeNative
  //type TypeSymb = RuntimeUniverse.TypeSymb
  //type MethodSymb = universe.MethodSymbol
}
