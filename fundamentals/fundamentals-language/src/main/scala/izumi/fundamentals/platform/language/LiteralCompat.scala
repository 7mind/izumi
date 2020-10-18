package izumi.fundamentals.platform.language

import scala.language.dynamics
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/** To compile code with literal types with 2.12, rewrite a literal like `mytype["abc"]` to `mytype[LiteralCompat.`"abc"`.T]` */
object LiteralCompat extends Dynamic {
  def selectDynamic(literal: String): { type T } = macro constantType

  def constantType(c: whitebox.Context)(literal: c.Tree): c.Tree = {
    import c.universe._
    import c.universe.internal.decorators._

    val resultType = c.typecheck(
      tq"{ type T = ${c.internal.constantType(c.parse(literal.asInstanceOf[LiteralApi].value.value.asInstanceOf[String]).asInstanceOf[LiteralApi].value)} }",
      c.TYPEmode,
      c.universe.definitions.NothingTpe,
      silent = false,
      withImplicitViewsDisabled = true,
      withMacrosDisabled = true,
    )
    Literal(Constant(())).setType(resultType.tpe)
  }
}
