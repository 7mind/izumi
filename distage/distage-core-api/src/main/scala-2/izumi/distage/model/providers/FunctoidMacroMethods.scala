package izumi.distage.model.providers

import izumi.distage.model.reflection.macros.FunctoidMacro
import scala.language.experimental.macros
import scala.language.implicitConversions

trait FunctoidMacroMethods {
  implicit def apply[R](fun: () => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (?) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[R]

}
