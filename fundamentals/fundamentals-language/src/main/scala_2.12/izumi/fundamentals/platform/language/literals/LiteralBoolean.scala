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

  @inline final def True: LiteralBoolean { type T = LiteralCompat.`true`.T } = new LiteralBoolean(true).asInstanceOf[LiteralBoolean { type T = LiteralCompat.`true`.T }]
  @inline final def False: LiteralBoolean { type T = LiteralCompat.`false`.T } = new LiteralBoolean(false).asInstanceOf[LiteralBoolean { type T = LiteralCompat.`false`.T }]

  object LiteralBooleanMacro {
    def createBool(c: whitebox.Context)(b: c.Expr[Boolean]): c.Tree = {
      import c.universe._
      val bool = b.tree.asInstanceOf[LiteralApi].value.value.asInstanceOf[Boolean]
      val methodName = TermName(bool.toString.capitalize)
      q"${reify(LiteralBoolean)}.$methodName"
    }
  }
}
