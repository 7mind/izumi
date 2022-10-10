package izumi.distage.model.definition.dsl

import izumi.distage.constructors.macros.AnyConstructorMacro
import scala.language.experimental.macros

import izumi.distage.constructors.FactoryConstructor
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.reflect.Tag
import scala.reflect.macros.blackbox

trait AbstractBindingDefDSLMacro[BindDSL[_], BindDSLAfterFrom[_], SetDSL[_]] { this: AbstractBindingDefDSL[BindDSL, BindDSLAfterFrom, SetDSL] =>

  final protected[this] def make[T]: BindDSL[T] = macro AnyConstructorMacro.make[BindDSL, T]

  final protected[this] def makeFactory[T](implicit tag: Tag[T], pos: CodePositionMaterializer): BindDSLAfterFrom[T] = macro
    AbstractBindingDefDSLMacroImpl.makeFactory[BindDSLAfterFrom, T]
}

object AbstractBindingDefDSLMacroImpl {

  def makeFactory[B[_], T: c.WeakTypeTag](c: blackbox.Context)(tag: c.Expr[Tag[T]], pos: c.Expr[CodePositionMaterializer]): c.Expr[B[T]] = {
    import c.universe._
    c.Expr[B[T]](q"""${c.prefix}._makeSimple[${weakTypeOf[T]}](${c.inferImplicitValue(weakTypeOf[FactoryConstructor[T]], silent = false)}.provider)($tag, $pos)""")
  }

}
