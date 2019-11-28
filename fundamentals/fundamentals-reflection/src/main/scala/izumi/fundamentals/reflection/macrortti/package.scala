package izumi.fundamentals.reflection

import scala.language.experimental.macros

package object macrortti {
  type LWeakTag[T] = LTag.Weak[T]
  object LWeakTag {
    def apply[T: LWeakTag]: LWeakTag[T] = implicitly
  }

  type LTagK[K[_]] = LTag.StrongHK[{ type Arg[A] = K[A] }]
  type LTagKK[K[_, _]] = LTag.StrongHK[{ type Arg[A, B] = K[A, B] }]
  type LTagK3[K[_, _, _]] = LTag.StrongHK[{ type Arg[A, B, C] = K[A, B, C]}]

  type LTagT[K[_[_]]] = LTag.StrongHK[{ type Arg[A[_]] = K[A]}]
  type LTagTK[K[_[_], _]] = LTag.StrongHK[{ type Arg[A[_], B] = K[A, B] }]
  type LTagTKK[K[_[_], _, _]] = LTag.StrongHK[{ type  Arg[A[_], B, C] = K[A, B, C] }]
  type LTagTK3[K[_[_], _, _, _]] = LTag.StrongHK[{ type Arg[A[_], B, C, D] = K[A, B, C, D] }]

  object LTagK {
    /**
    * Construct a type tag for a higher-kinded type `K[_]`
    *
    * Example:
    * {{{
    *     LTagK[Option]
    * }}}
    **/
    def apply[K[_]: LTagK]: LTagK[K] = implicitly
  }

  object LTagKK {
    def apply[K[_, _]: LTagKK]: LTagKK[K] = implicitly
  }

  object LTagK3 {
    def apply[K[_, _, _]: LTagK3]: LTagK3[K] = implicitly
  }

  object LTagT {
    def apply[K[_[_]]: LTagT]: LTagT[K] = implicitly
  }

  object LTagTK {
    def apply[K[_[_], _]: LTagTK]: LTagTK[K] = implicitly
  }

  object LTagTKK {
    def apply[K[_[_], _, _]: LTagTKK]: LTagTKK[K] = implicitly
  }

  object LTagTK3 {
    def apply[K[_[_], _, _, _]: LTagTK3]: LTagTK3[K] = implicitly
  }

  // simple materializers
  def LTT[T]: LightTypeTag = macro LightTypeTagMacro.makeParsedLightTypeTag[T]
  def `LTT[_]`[T[_]]: LightTypeTag = macro LightTypeTagMacro.makeParsedLightTypeTag[T[Nothing]]
  def `LTT[+_]`[T[+ _]]: LightTypeTag = macro LightTypeTagMacro.makeParsedLightTypeTag[T[Nothing]]
  def `LTT[A,B,_>:B<:A]`[A, B <: A, T[_ >: B <: A]]: LightTypeTag = macro LightTypeTagMacro.makeParsedLightTypeTag[T[Nothing]]
  def `LTT[_[_]]`[T[_[_]]]: LightTypeTag = macro LightTypeTagMacro.makeParsedLightTypeTag[T[Nothing]]
  def `LTT[_[_[_]]]`[T[_[_[_]]]]: LightTypeTag = macro LightTypeTagMacro.makeParsedLightTypeTag[T[Nothing]]
  def `LTT[_,_]`[T[_, _]]: LightTypeTag = macro LightTypeTagMacro.makeParsedLightTypeTag[T[Nothing, Nothing]]
  def `LTT[_[_],_[_]]`[T[_[_], _[_]]]: LightTypeTag = macro LightTypeTagMacro.makeParsedLightTypeTag[T[Nothing, Nothing]]
  def `LTT[_[_[_],_[_]]]`[T[_[_[_], _[_]]]]: LightTypeTag = macro LightTypeTagMacro.makeParsedLightTypeTag[T[Nothing]]

}
