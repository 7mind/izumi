package izumi.distage.reflection.macros

import scala.language.experimental.macros
import scala.language.implicitConversions

trait FunctoidMacroMethods[Ftoid[+_]] {
  implicit def apply[R](fun: () => R): Ftoid[R] = macro FunctoidMacro.impl[R, Ftoid]
  implicit def apply[R](fun: (?) => R): Ftoid[R] = macro FunctoidMacro.impl[R, Ftoid]
  implicit def apply[R](fun: (?, ?) => R): Ftoid[R] = macro FunctoidMacro.impl[R, Ftoid]
  implicit def apply[R](fun: (?, ?, ?) => R): Ftoid[R] = macro FunctoidMacro.impl[R, Ftoid]
  implicit def apply[R](fun: (?, ?, ?, ?) => R): Ftoid[R] = macro FunctoidMacro.impl[R, Ftoid]
  implicit def apply[R](fun: (?, ?, ?, ?, ?) => R): Ftoid[R] = macro FunctoidMacro.impl[R, Ftoid]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?) => R): Ftoid[R] = macro FunctoidMacro.impl[R, Ftoid]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?) => R): Ftoid[R] = macro FunctoidMacro.impl[R, Ftoid]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?) => R): Ftoid[R] = macro FunctoidMacro.impl[R, Ftoid]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Ftoid[R] = macro FunctoidMacro.impl[R, Ftoid]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Ftoid[R] = macro FunctoidMacro.impl[R, Ftoid]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Ftoid[R] = macro FunctoidMacro.impl[R, Ftoid]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Ftoid[R] = macro FunctoidMacro.impl[R, Ftoid]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Ftoid[R] = macro FunctoidMacro.impl[R, Ftoid]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Ftoid[R] = macro FunctoidMacro.impl[R, Ftoid]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Ftoid[R] = macro FunctoidMacro.impl[R, Ftoid]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Ftoid[R] = macro FunctoidMacro.impl[R, Ftoid]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Ftoid[R] = macro FunctoidMacro.impl[R, Ftoid]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Ftoid[R] = macro FunctoidMacro.impl[R, Ftoid]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Ftoid[R] = macro FunctoidMacro.impl[R, Ftoid]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Ftoid[R] = macro FunctoidMacro.impl[R, Ftoid]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Ftoid[R] = macro FunctoidMacro.impl[R, Ftoid]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Ftoid[R] = macro FunctoidMacro.impl[R, Ftoid]
}
