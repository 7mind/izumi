package izumi.fundamentals.platform.language.literals

import scala.language.experimental.macros
import scala.language.implicitConversions
import scala.reflect.macros.whitebox

final class LiteralBoolean(private val value: Boolean) extends AnyVal {
  type T <: Boolean
}

object LiteralBoolean {
  type Of[T0 <: Boolean] = LiteralBoolean { type T = T0 }
  type Get[L <: LiteralBoolean] = L#T

  @inline implicit final def apply(b: Boolean): LiteralBoolean = macro LiteralBooleanMacro.createBool
  @inline implicit final def unwrap[L <: LiteralBoolean](literalBoolean: L): L#T = literalBoolean.value.asInstanceOf[L#T]

  @inline final def True: True = new LiteralBoolean(true).asInstanceOf[LiteralBoolean { type T = LiteralCompat.`true`.T }]
  @inline final def False: False = new LiteralBoolean(false).asInstanceOf[LiteralBoolean { type T = LiteralCompat.`false`.T }]

  type True = LiteralBoolean { type T = LiteralCompat.`true`.T }
  type False = LiteralBoolean { type T = LiteralCompat.`false`.T }

  object LiteralBooleanMacro {
    def createBool(c: whitebox.Context)(b: c.Expr[Boolean]): c.Tree = {
      import c.universe._
      val bool = b.tree match {
        case l: LiteralApi => l.value.value.asInstanceOf[Boolean]
        case o => c.abort(c.enclosingPosition, s"Not a literal Boolean: `${showCode(o)}`, only `true` or `false` are allowed here")
      }
      val methodName = TermName(bool.toString.capitalize)
      q"${reify(LiteralBoolean)}.$methodName"
    }
  }

  def compileTimeIf[Bool <: Boolean, A, B](cond: Bool)(ifTrue: A)(ifFalse: B): Any = macro CompileTimeIfMacro.compileTimeIfImpl[Bool, A, B]

  object CompileTimeIfMacro {
    def compileTimeIfImpl[Bool <: Boolean, A, B](c: whitebox.Context)(cond: c.Expr[Bool])(ifTrue: c.Expr[A])(ifFalse: c.Expr[B]): c.Expr[Any] = {
      val bool = cond.actualType match {
        case c: c.universe.ConstantTypeApi =>
          c.value.value.asInstanceOf[Boolean]
        case tpe =>
          c.abort(c.enclosingPosition, s"Not a literal boolean expr=$cond tpe=$tpe")
      }
      if (bool) ifTrue else ifFalse
    }
  }
}
