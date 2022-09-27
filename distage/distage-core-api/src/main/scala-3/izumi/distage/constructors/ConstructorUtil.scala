package izumi.distage.constructors

import izumi.fundamentals.platform.reflection.ReflectionUtil

object ConstructorUtil {

  import scala.quoted.{Expr, Quotes, Type}

  def requireConcreteTypeConstructor[R: Type](macroName: String)(using qctx: Quotes): Unit = {
    import qctx.reflect.*
    val tpe = TypeRepr.of[R]
    if (!ReflectionUtil.allPartsStrong(tpe)) {
      val hint = tpe.dealias.show
      report.errorAndAbort(
        s"""$macroName: Can't generate constructor for ${tpe.show}:
           |Type constructor is an unresolved type parameter `$hint`.
           |Did you forget to put a $macroName context bound on the $hint, such as [$hint: $macroName]?
           |""".stripMargin,
      )
    }
  }
}
