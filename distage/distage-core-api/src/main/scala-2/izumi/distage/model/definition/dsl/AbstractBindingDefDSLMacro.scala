package izumi.distage.model.definition.dsl

import izumi.distage.constructors.FactoryConstructor
import izumi.distage.constructors.macros.AnyConstructorMacro
import izumi.distage.model.providers.Functoid
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.reflect.Tag

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait AbstractBindingDefDSLMacro[BindDSL[_], BindDSLAfterFrom[_], SetDSL[_]] { this: AbstractBindingDefDSL[BindDSL, BindDSLAfterFrom, SetDSL] =>
  import AbstractBindingDefDSLMacroImpl.*

  final protected[this] def make[T]: BindDSL[T] = macro AnyConstructorMacro.make[BindDSL, T]

  final protected[this] def makeFactory[T](implicit tag: Tag[T], pos: CodePositionMaterializer): BindDSLAfterFrom[T] = macro makeFactoryOut[BindDSLAfterFrom, T]
}

trait AbstractModuleDefDSLMacro[T, AfterBind] { this: ModuleDefDSL.MakeDSLBase[T, AfterBind] =>
  import AbstractBindingDefDSLMacroImpl.*
  final def fromFactory[I <: T]: AfterBind = macro makeFactoryImpl[AfterBind, I]
}

object AbstractBindingDefDSLMacroImpl {
  def makeFactoryProvider[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Functoid[T]] = {
    import c.universe.*
    c.Expr[Functoid[T]](q"""${c.inferImplicitValue(weakTypeOf[FactoryConstructor[T]], silent = false)}.provider""")
  }

  def makeFactoryOut[B[_], T: c.WeakTypeTag](c: blackbox.Context)(tag: c.Expr[Tag[T]], pos: c.Expr[CodePositionMaterializer]): c.Expr[B[T]] = {
    import c.universe.*
    c.Expr[B[T]](q"""${c.prefix}._makeSimple[${weakTypeOf[T]}](${makeFactoryProvider[T](c)})($tag, $pos)""")
  }

  def makeFactoryImpl[B, T: c.WeakTypeTag](c: blackbox.Context): c.Expr[B] = {
    import c.universe.*

    c.Expr[B](q"""
              ${c.prefix}.bind(
                _root_.izumi.distage.model.definition.ImplDef.ProviderImpl(
                  _root_.izumi.distage.model.reflection.SafeType.get[${weakTypeOf[T]}], 
                  ${makeFactoryProvider[T](c)}.get
                )
              )""")
  }
}
