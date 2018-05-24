package com.github.pshirshov.izumi.distage.model.providers

import com.github.pshirshov.izumi.distage.model.reflection.macros.DIKeyWrappedFunctionMacroImpl
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.language.experimental.macros
import scala.language.implicitConversions

/**
* A function that receives its arguments from DI context, including named instances via @Id annotation.
*
* Prefer passing a method reference such as (method _)
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
* The following IS NOT SUPPORTED, annotations are lost when converting method to a function value:
*
*   def constructorMethod(@Id("special") i: Int): Unit = ()
*
*   val constructor = constructorMethod _
*
*   Bindings.provider[Unit](constructor) // Will summon regular Int, not a "special" Int from DI context
*
* */
class DIKeyWrappedFunction[+R](val associations: Seq[Association.Parameter]
                             , val ret: TypeFull
                             , val fun: Seq[Any] => Any) extends Provider {

  def diKeys: Seq[DIKey] = associations.map(_.wireWith)

  override protected def call(args: Any*): R = {
    val seq: Seq[Any] = args
    fun.apply(seq).asInstanceOf[R]
  }

  override def toString: String = {
    s"$fun(${argTypes.mkString(", ")}): $ret"
  }

  override def unsafeApply(refs: TypedRef[_]*): R = super.unsafeApply(refs: _*).asInstanceOf[R]
}

object DIKeyWrappedFunction {

  def apply[R: Tag](associations: Seq[Association.Parameter], fun: Seq[Any] => Any): DIKeyWrappedFunction[R] =
    new DIKeyWrappedFunction[R](associations, SafeType.get[R], fun)

  implicit def apply[R](funcExpr: () => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
  implicit def apply[R](funcExpr: _ => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
  implicit def apply[R](funcExpr: (_, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
  implicit def apply[R](funcExpr: (_, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
  implicit def apply[R](funcExpr: (_, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
  implicit def apply[R](funcExpr: (_, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
  implicit def apply[R](funcExpr: (_, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
  implicit def apply[R](funcExpr: (_, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
  implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
  implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
  implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
  implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
  implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
  implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
  implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
  implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
  implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
  implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
  implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
  implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
  implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
  implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
  implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]

}


