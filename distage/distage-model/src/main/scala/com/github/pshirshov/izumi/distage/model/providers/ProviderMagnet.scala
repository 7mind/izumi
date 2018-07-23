package com.github.pshirshov.izumi.distage.model.providers

import com.github.pshirshov.izumi.distage.model.exceptions.TODOBindingException
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.{Provider, DIKey}
import com.github.pshirshov.izumi.distage.model.reflection.macros.{ProviderMagnetMacro, ProviderMagnetMacroGenerateUnsafeWeakSafeTypes}
import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer

import scala.language.implicitConversions
import scala.language.experimental.macros

/**
* A function that receives its arguments from DI context, including named instances via [[com.github.pshirshov.izumi.distage.model.definition.Id]] annotation.
*
* Prefer passing an inline lambda such as { x => y } or a method reference such as (method _)
*
* The following syntaxes are supported by extractor macro:
*
* Inline lambda:
*
*   Bindings.provider[Unit] {
*     i: Int @Id("special") => ()
*   }
*
* Method reference:
*
*   def constructor(@Id("special") i: Int): Unit = ()
*
*   Bindings.provider[Unit](constructor _)
*
* Function value with annotated signature:
*
*   val constructor: Int @Id("special") => Unit = _ => ()
*
*   Bindings.provider[Unit](constructor)
*
* The following IS NOT SUPPORTED, because annotations are lost when converting a method into a function value:
*
*   def constructorMethod(@Id("special") i: Int): Unit = ()
*
*   val constructor = constructorMethod _
*
*   Bindings.provider[Unit](constructor) // Will summon regular Int, not a "special" Int from DI context
*
* Annotations on constructor will also be lost when passing a case classes .apply method, use `new` instead.
*
* DO:
*
*   Bindings.provider(new Abc(_, _, _))
*
* DON'T:
*
*   Bindings.provider(Abc.apply _)
*
* */
case class ProviderMagnet[+R](get: Provider)

object ProviderMagnet {
  implicit def apply[R](fun: () => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: _ => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]

  def todoProvider(key: DIKey)(implicit pos: CodePositionMaterializer): ProviderMagnet[_] =
    new ProviderMagnet[Any](
      Provider.ProviderImpl(
        associations = Seq.empty
        , ret = key.tpe
        , fun = _ => throw new TODOBindingException(
          s"Tried to instantiate a 'TODO' binding for $key defined at ${pos.get}!", key, pos)
      )
    )

  def generateUnsafeWeakSafeTypes[R](fun: Any): ProviderMagnet[R] = macro ProviderMagnetMacroGenerateUnsafeWeakSafeTypes.impl[R]

}
