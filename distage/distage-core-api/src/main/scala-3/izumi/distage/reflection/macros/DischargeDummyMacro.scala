package izumi.distage.reflection.macros

import izumi.distage.model.providers.Functoid

import scala.annotation.tailrec
import scala.quoted.{Expr, Quotes, Type}

object DischargeDummyMacro {
  inline def dischargeDummy[I, N](inline function: N ?=> Functoid[I]): Functoid[I] = ${ dischargeDummyImpl[I, N]('function) }

  def dischargeDummyImpl[I: Type, N: Type](
    function: Expr[N ?=> Functoid[I]]
  )(using qctx: Quotes
  ): Expr[Functoid[I]] = {
    val implicitsExtractorMacro = new DummyImplicitsExtractorMacro[qctx.type]()
    new DischargeDummyMacroImpl[qctx.type](implicitsExtractorMacro).rewriteDummies[I, N](function)
  }

  private final class DischargeDummyMacroImpl[Q <: Quotes](
    using val qctx: Q
  )(dummyImplicitsExtractorMacro: DummyImplicitsExtractorMacro[qctx.type]
  ) {
    import qctx.reflect.*
    private val dummyTypeSymbol: Symbol = TypeRepr.of[FunctoidDummyImplicit].typeSymbol
    def rewriteDummies[I: Type, N: Type](
      function: Expr[N ?=> Functoid[I]]
    ): Expr[Functoid[I]] = {
      @tailrec
      def rewrite(fun: Term): Expr[Functoid[I]] = {
        fun match {
          case block @ Block(List(DefDef(name, (singleParamList: TermParamClause) :: Nil, _, Some(body))), _: Closure) =>
            def rewriteDummiesByIdent(
              tree: Tree,
              dummyArgs: Map[Term, Term],
              owner: Symbol,
            ): Tree = {
              val treeMap: TreeMap = new TreeMap {
                override def transformTerm(tree: qctx.reflect.Term)(owner: qctx.reflect.Symbol): qctx.reflect.Term = {
                  tree match {
                    case i: Ident => dummyArgs.getOrElse(i, i)
                    case _ => super.transformTerm(tree)(owner)
                  }
                }
              }
              treeMap.transformTree(tree)(owner)
            }

            val dummyArgs = dummyImplicitsExtractorMacro.extractDummyArguments(body, Symbol.spliceOwner)
            if (dummyArgs.nonEmpty) {
              val dummyParamSymbol = singleParamList.params.head.symbol
              val lambdaArgsNames = dummyArgs.map(t => "explicit$" + t.term.symbol.name)
              val lambdaArgsTypes = dummyArgs.map(_.tpe)

              val methodType = MethodType(lambdaArgsNames)(
                _ => lambdaArgsTypes,
                _ => body.tpe,
              )

              val lambda = Lambda(
                Symbol.spliceOwner,
                methodType,
                (owner, args) => {
                  val byDummy = dummyArgs.map(_.term).zip(args.map(_.asExpr.asTerm)).toMap
                  rewriteDummiesByIdent(body, byDummy, owner)
                },
              )

              val implicits = dummyArgs.map(
                dummy =>
                  dummy.tpe.widen.asType match {
                    case '[a] =>
                      Expr
                        .summonIgnoring[a](dummyParamSymbol).map(_.asTerm)
                        .getOrElse(report.errorAndAbort(s"No implicit value found for ${dummy.tpe.show}, ${dummy.tpe.widenTermRefByName} "))
                    case t => report.errorAndAbort(s"Failed to perform an implicit search for $t")
                  }
              )

              val anyTpe = TypeRepr.of[Any]
              val fnType = defn.FunctionClass(implicits.size).typeRef.appliedTo(List.fill(implicits.size + 1)(anyTpe))
              Select.unique(lambda, "apply").appliedToArgs(implicits).asExprOf[Functoid[I]]
            } else {
              val res = '{ ${ function }(using null.asInstanceOf[N]) }
              println(s"discharge not done: ${res.show}")
              res
            }

          case Typed(term, _) => rewrite(term)
          case Inlined(_, _, term) => rewrite(term)
          case Block(List(), term) => rewrite(term)
          case _ =>
            val term = fun.asExprOf[N ?=> Functoid[I]]
            '{ ${ term }(using null.asInstanceOf[N]) }
        }
      }

      rewrite(function.asTerm)
    }
  }
}
