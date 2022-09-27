package izumi.distage.constructors

import izumi.distage.model.providers.Functoid


object ClassConstructorMacro {
  import scala.quoted.{Expr, Quotes, Type}

  def make[R: Type](using qctx: Quotes): Expr[ClassConstructor[R]] = {
    import qctx.reflect.*

    Expr.summon[ValueOf[R]] match {
      case Some(valexpr) =>
        '{new ClassConstructor[R](Functoid.singleton(${valexpr.asExprOf[scala.Singleton & R]}))}
      case o =>
        ConstructorUtil.requireConcreteTypeConstructor[R]("ClassConstructor")
        '{new ClassConstructor[R](scala.compiletime.summonInline[Functoid[R]])}
    }
  }
}