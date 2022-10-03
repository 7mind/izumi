package izumi.distage.constructors

import izumi.fundamentals.platform.reflection.ReflectionUtil

object ConstructorUtil {

  import scala.quoted.{Expr, Quotes, Type}

  def requireConcreteTypeConstructor[R: Type](macroName: String)(using qctx: Quotes): Unit = {
    import qctx.reflect.*
    val tpe = TypeRepr.of[R]
    if (!ReflectionUtil.allPartsStrong(tpe.typeSymbol.typeRef)) {
      val hint = tpe.dealias.show
      report.errorAndAbort(
        s"""$macroName: Can't generate constructor for ${tpe.show}:
           |Type constructor is an unresolved type parameter `$hint`.
           |Did you forget to put a $macroName context bound on the $hint, such as [$hint: $macroName]?
           |""".stripMargin
      )
    }
  }

  def wrapApplicationIntoLambda[Q <: Quotes, R: Type](
    using qctx: Q
  )(paramss: List[List[(String, qctx.reflect.TypeTree)]],
    constructorTerm: qctx.reflect.Term,
  ): Expr[Any] = {
    wrapIntoLambda[Q, R](paramss) {
      (_, args0) =>
        import qctx.reflect.*
        import scala.collection.immutable.Queue
        val (_, argsLists) = paramss.foldLeft((args0, Queue.empty[List[Term]])) {
          case ((args, res), params) =>
            val (argList, rest) = args.splitAt(params.size)
            (rest, res :+ (argList: List[Tree]).asInstanceOf[List[Term]])
        }

        val appl = argsLists.foldLeft(constructorTerm)(_.appliedToArgs(_))
        val trm = Typed(appl, TypeTree.of[R])
        trm
    }
  }

  def wrapIntoLambda[Q <: Quotes, R: Type](
    using qctx: Q
  )(paramss: List[List[(String, qctx.reflect.TypeTree)]]
  )(body: (qctx.reflect.Symbol, List[qctx.reflect.Term]) => qctx.reflect.Tree
  ): Expr[Any] = {
    import qctx.reflect.*
    import scala.collection.immutable.Queue

    val params = paramss.flatten
    val mtpe = MethodType(params.map(_._1))(_ => params.map(_._2.tpe), _ => TypeRepr.of[R])
    val lam = Lambda(
      Symbol.spliceOwner,
      mtpe,
      {
        case (lamSym, args0) =>
          body(lamSym, args0.asInstanceOf[List[Term]])
      },
    )
    lam.asExpr
  }
}
