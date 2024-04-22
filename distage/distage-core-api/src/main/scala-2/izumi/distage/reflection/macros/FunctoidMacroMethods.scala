package izumi.distage.reflection.macros

import izumi.distage.model.providers.Functoid

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
