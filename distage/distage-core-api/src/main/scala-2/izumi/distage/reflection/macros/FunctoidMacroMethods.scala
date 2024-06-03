package izumi.distage.reflection.macros

import izumi.distage.model.providers.FunctoidTemplate

import scala.language.experimental.macros
import scala.language.implicitConversions

trait FunctoidMacroMethodsTemplate { this: FunctoidTemplate =>

  trait FunctoidMacroMethods {
    type FXOid[+A] = Functoid[A]

    implicit def apply[R](fun: () => R): Functoid[R] = macro FunctoidMacro.impl[Functoid, R]
    implicit def apply[R](fun: (?) => R): Functoid[R] = macro FunctoidMacro.impl[Functoid, R]
    implicit def apply[R](fun: (?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[Functoid, R]
    implicit def apply[R](fun: (?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[Functoid, R]
    implicit def apply[R](fun: (?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[Functoid, R]
    implicit def apply[R](fun: (?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[Functoid, R]
    implicit def apply[R](fun: (?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[Functoid, R]
    implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[Functoid, R]
    implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[Functoid, R]
    implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[Functoid, R]
    implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[Functoid, R]
    implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[Functoid, R]
    implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[Functoid, R]
    implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[Functoid, R]
    implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[Functoid, R]
    implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[Functoid, R]
    implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[Functoid, R]
    implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[Functoid, R]
    implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[Functoid, R]
    implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[Functoid, R]
    implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[Functoid, R]
    implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[Functoid, R]
    implicit def apply[R](fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = macro FunctoidMacro.impl[Functoid, R]
  }

}
